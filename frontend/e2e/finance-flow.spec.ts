import { expect, test, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const evidenceDirectory = path.resolve(
  process.cwd(),
  "../docs/evidence/stage-20",
);
const invoicePaths = {
  paid:
    process.env.STAGE20_PAID_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage20-delta.png"),
  scheduled:
    process.env.STAGE20_SCHEDULED_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage20-everline.png"),
  stale:
    process.env.STAGE20_STALE_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage20-frontier.png"),
  preapproval:
    process.env.STAGE20_PREAPPROVAL_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage20-grove.png"),
};

test.beforeAll(async () => {
  await mkdir(evidenceDirectory, { recursive: true });
});

test("finance processes paid and scheduled requests and enforces scope and version state", async ({
  context,
  page,
}) => {
  await login(page, "employee1");
  const paidRequestUrl = await uploadInvoice(page, invoicePaths.paid);
  await page.getByRole("link", { name: "Back to requests" }).click();
  const scheduledRequestUrl = await uploadInvoice(page, invoicePaths.scheduled);
  await page.getByRole("link", { name: "Back to requests" }).click();
  const staleRequestUrl = await uploadInvoice(page, invoicePaths.stale);
  await page.getByRole("link", { name: "Back to requests" }).click();
  const preapprovalRequestUrl = await uploadInvoice(
    page,
    invoicePaths.preapproval,
  );

  await page.goto("/finance/queue");
  await expect(page).toHaveURL(/\/employee\/requests$/);
  await logout(page);

  await login(page, "manager1");
  await approveFromQueue(page, "Delta Design");
  await approveFromQueue(page, "Everline Logistics");
  await approveFromQueue(page, "Frontier Labs");
  await page.goto("/finance/queue");
  await expect(page).toHaveURL(/\/manager\/queue$/);
  await logout(page);

  const initialQueueResponsePromise = page.waitForResponse(
    isFinanceDashboardResponse,
  );
  await login(page, "finance1");
  const initialQueueResponse = await initialQueueResponsePromise;
  expect(initialQueueResponse.status()).toBe(200);
  const initialQueue = await initialQueueResponse.json();
  expect(initialQueue.sort).toEqual({
    field: "updated_at",
    direction: "asc",
  });
  expect(initialQueue.total_elements).toBe(3);
  await expect(page.getByRole("table")).toContainText("Delta Design");
  await expect(page.getByRole("table")).toContainText("Everline Logistics");
  await expect(page.getByRole("table")).toContainText("Frontier Labs");
  await expect(page.getByRole("table")).not.toContainText("Grove Media");
  await page.screenshot({
    path: path.join(evidenceDirectory, "01-finance-queue.png"),
    fullPage: true,
  });

  await rowFor(page, "Delta Design")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(paidRequestUrl.replace("/employee/", "/finance/"));
  await processVisibleRequest(page, "paid", 2);
  await expect(page.getByText("Processed", { exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toContainText("marked as paid");
  await expect(page.getByText(/Processed as/)).toContainText("paid");
  await page.screenshot({
    path: path.join(evidenceDirectory, "02-paid-detail.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to Finance queue" }).click();
  await expect(page.getByRole("table")).not.toContainText("Delta Design");
  await expect(page.getByRole("table")).toContainText("Everline Logistics");
  await expect(page.getByRole("table")).toContainText("Frontier Labs");
  await page.screenshot({
    path: path.join(evidenceDirectory, "03-queue-after-paid.png"),
    fullPage: true,
  });

  await logout(page);
  await login(page, "finance2");
  await expect(page.getByRole("table")).toContainText("Everline Logistics");
  await rowFor(page, "Everline Logistics")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(
    scheduledRequestUrl.replace("/employee/", "/finance/"),
  );
  await processVisibleRequest(page, "scheduled", 2);
  await expect(page.getByText(/Processed as/)).toContainText("scheduled");
  await expect(page.getByRole("status")).toContainText("payment scheduled");
  await page.screenshot({
    path: path.join(evidenceDirectory, "04-scheduled-detail.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to Finance queue" }).click();
  await rowFor(page, "Frontier Labs")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(
    staleRequestUrl.replace("/employee/", "/finance/"),
  );
  const staleRequestId = staleRequestUrl.split("/").at(-1);
  const cookies = await context.cookies("http://localhost:8080");
  const csrf = cookies.find((cookie) => cookie.name === "XSRF-TOKEN");
  expect(csrf).toBeDefined();
  let concurrentStatus = 0;
  let concurrentPaymentStatus = "";
  await page.route(
    "**/api/v1/requests/*/process",
    async (route) => {
      const concurrentProcess = await context.request.post(
        `http://localhost:8080/api/v1/requests/${staleRequestId}/process`,
        {
          data: { expected_version: 2, payment_status: "paid" },
          headers: { "X-XSRF-TOKEN": decodeURIComponent(csrf!.value) },
        },
      );
      concurrentStatus = concurrentProcess.status();
      concurrentPaymentStatus = (await concurrentProcess.json()).processing
        .payment_status;
      await route.continue();
    },
    { times: 1 },
  );
  await page.getByRole("button", { name: "Mark processed" }).click();
  await page.getByRole("radio", { name: /^Scheduled/ }).check();
  const conflictResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/process"),
  );
  await page.getByRole("button", { name: "Confirm processing" }).click();
  const conflictResponse = await conflictResponsePromise;
  expect(concurrentStatus).toBe(200);
  expect(concurrentPaymentStatus).toBe("paid");
  expect(conflictResponse.status()).toBe(409);
  expect((await conflictResponse.json()).error.code).toBe("STATE_CONFLICT");
  await expect(page.locator(".alert.error")).toContainText(
    "already been processed",
  );
  await expect(page.getByText("Processed", { exact: true })).toBeVisible();
  await expect(page.getByText(/Processed as/)).toContainText("paid");
  await page.screenshot({
    path: path.join(evidenceDirectory, "05-stale-process-conflict.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to Finance queue" }).click();
  await expect(page.getByText("No invoices awaiting processing")).toBeVisible();
  const processedResponsePromise = page.waitForResponse(
    (response) =>
      isFinanceDashboardResponse(response) &&
      response.url().includes("status=processed"),
  );
  await page
    .getByRole("combobox", { name: "View" })
    .selectOption("processed");
  const processedResponse = await processedResponsePromise;
  expect(processedResponse.status()).toBe(200);
  const processedQueue = await processedResponse.json();
  expect(processedQueue.sort).toEqual({
    field: "processed_at",
    direction: "desc",
  });
  expect(processedQueue.total_elements).toBe(3);
  await expect(page.getByRole("table")).toContainText("Delta Design");
  await expect(page.getByRole("table")).toContainText("Everline Logistics");
  await expect(page.getByRole("table")).toContainText("Frontier Labs");
  await page.screenshot({
    path: path.join(evidenceDirectory, "06-processed-filter.png"),
    fullPage: true,
  });

  await page.goto(
    preapprovalRequestUrl.replace("/employee/", "/finance/"),
  );
  await expect(
    page.getByRole("heading", { name: "Request unavailable" }),
  ).toBeVisible();
  await expect(page.getByText(/has not reached Finance review/)).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "07-preapproval-404.png"),
    fullPage: true,
  });

  console.log(
    "finance_flow=queue:3 paid:finance1 scheduled:finance2 conflict:409 processed:3 preapproval:404 route_guard:employee+manager",
  );
});

async function login(page: Page, identifier: string) {
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();
  await page.getByLabel("Login identifier").fill(identifier);
  await page.getByLabel("Password").fill("OrbisFlow123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(
    identifier.startsWith("employee")
      ? /\/employee\/requests$/
      : identifier.startsWith("manager")
        ? /\/manager\/queue$/
        : /\/finance\/queue$/,
    { timeout: 20_000 },
  );
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
}

async function uploadInvoice(page: Page, invoicePath: string) {
  await page.goto("/employee/requests/new");
  await page.locator('input[type="file"]').setInputFiles(invoicePath);
  await page.getByRole("button", { name: "Submit invoice" }).click();
  await expect(page).toHaveURL(/\/employee\/requests\/[0-9a-f-]+$/, {
    timeout: 30_000,
  });
  await expect(
    page.getByText("Manager review", { exact: true }),
  ).toBeVisible({ timeout: 90_000 });
  return page.url();
}

async function approveFromQueue(page: Page, vendor: string) {
  await page.goto("/manager/queue");
  await rowFor(page, vendor).getByRole("link", { name: /View/ }).click();
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/approve"),
  );
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page.getByRole("button", { name: "Confirm approval" }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  expect((await response.json()).status).toBe("finance_review");
}

async function processVisibleRequest(
  page: Page,
  paymentStatus: "paid" | "scheduled",
  expectedVersion: number,
) {
  await page.getByRole("button", { name: "Mark processed" }).click();
  await page
    .getByRole("radio", {
      name: paymentStatus === "paid" ? /^Paid/ : /^Scheduled/,
    })
    .check();
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/process"),
  );
  await page.getByRole("button", { name: "Confirm processing" }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  expect(response.request().postDataJSON()).toEqual({
    expected_version: expectedVersion,
    payment_status: paymentStatus,
  });
  expect((await response.json()).processing.payment_status).toBe(paymentStatus);
}

function rowFor(page: Page, vendor: string) {
  return page.getByRole("row").filter({ hasText: vendor });
}

function isFinanceDashboardResponse(response: {
  request(): { method(): string };
  url(): string;
}) {
  return (
    response.request().method() === "GET" &&
    response.url().includes("/dashboards/finance/requests")
  );
}
