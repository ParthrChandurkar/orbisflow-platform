import { expect, test, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const evidenceDirectory = path.resolve(
  process.cwd(),
  "../docs/evidence/stage-19",
);
const approveInvoicePath =
  process.env.STAGE19_APPROVE_INVOICE_PATH ??
  path.join(process.env.TEMP ?? ".", "orbisflow-stage19-atlas.png");
const rejectInvoicePath =
  process.env.STAGE19_REJECT_INVOICE_PATH ??
  path.join(process.env.TEMP ?? ".", "orbisflow-stage19-beacon.png");
const staleInvoicePath =
  process.env.STAGE19_STALE_INVOICE_PATH ??
  path.join(process.env.TEMP ?? ".", "orbisflow-stage19-cascade.png");

test.beforeAll(async () => {
  await mkdir(evidenceDirectory, { recursive: true });
});

test("manager approves, rejects, filters, and handles a stale decision", async ({
  context,
  page,
}) => {
  await login(page, "employee1");
  const approveRequestUrl = await uploadInvoice(page, approveInvoicePath);
  await page.getByRole("link", { name: "Back to requests" }).click();
  const rejectRequestUrl = await uploadInvoice(page, rejectInvoicePath);
  await page.getByRole("link", { name: "Back to requests" }).click();
  const staleRequestUrl = await uploadInvoice(page, staleInvoicePath);

  await page.goto("/manager/queue");
  await expect(page).toHaveURL(/\/employee\/requests$/);
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();

  const queueResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "GET" &&
      response.url().includes("/dashboards/manager/requests"),
  );
  const activityResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "GET" &&
      response.url().endsWith("/dashboards/manager/team-activity"),
  );
  await login(page, "manager1");
  const queueResponse = await queueResponsePromise;
  const queueBody = await queueResponse.json();
  expect(queueBody.sort).toEqual({ field: "updated_at", direction: "asc" });
  expect(queueBody.total_elements).toBe(3);
  const activityResponse = await activityResponsePromise;
  expect(await activityResponse.json()).toEqual({
    pending: 3,
    approved: 0,
    rejected: 0,
  });
  await expect(page.locator(".activity-card.pending")).toContainText("3");
  await expect(page.getByRole("table")).toContainText("Atlas Advisory");
  await expect(page.getByRole("table")).toContainText("Beacon Operations");
  await expect(page.getByRole("table")).toContainText("Cascade Consulting");
  await page.screenshot({
    path: path.join(evidenceDirectory, "01-manager-queue.png"),
    fullPage: true,
  });

  await rowFor(page, "Atlas Advisory")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(
    approveRequestUrl.replace("/employee/", "/manager/"),
  );
  await expect(
    page.getByRole("heading", { name: "Atlas Advisory", exact: true }),
  ).toBeVisible();
  const approveResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/approve"),
  );
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await page.getByRole("button", { name: "Confirm approval" }).click();
  const approveResponse = await approveResponsePromise;
  expect(approveResponse.status()).toBe(200);
  expect(approveResponse.request().postDataJSON()).toEqual({
    expected_version: 1,
  });
  expect((await approveResponse.json()).status).toBe("finance_review");
  await expect(page.getByText("Finance review", { exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toContainText(
    "approved and routed to Finance",
  );
  await page.screenshot({
    path: path.join(evidenceDirectory, "02-approved-detail.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to approval queue" }).click();
  await expect(page.getByRole("table")).not.toContainText("Atlas Advisory");
  await expect(page.getByRole("table")).toContainText("Beacon Operations");
  await expect(page.getByRole("table")).toContainText("Cascade Consulting");
  await expect(page.locator(".activity-card.pending")).toContainText("2");
  await expect(page.locator(".activity-card.approved")).toContainText("1");
  await page.screenshot({
    path: path.join(evidenceDirectory, "03-queue-after-approval.png"),
    fullPage: true,
  });

  await rowFor(page, "Beacon Operations")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(
    rejectRequestUrl.replace("/employee/", "/manager/"),
  );

  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await page.getByRole("button", { name: "Confirm rejection" }).click();
  await expect(page.getByText("Enter a rejection reason.")).toBeVisible();
  await page
    .getByLabel("Rejection reason")
    .fill("Cost center evidence is missing.");
  const rejectResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/reject"),
  );
  await page.getByRole("button", { name: "Confirm rejection" }).click();
  const rejectResponse = await rejectResponsePromise;
  expect(rejectResponse.status()).toBe(200);
  expect(rejectResponse.request().postDataJSON()).toEqual({
    expected_version: 1,
    reason: "Cost center evidence is missing.",
  });
  expect((await rejectResponse.json()).status).toBe("rejected");
  await expect(page.getByText("Rejected", { exact: true })).toBeVisible();
  await expect(page.getByText("Cost center evidence is missing.")).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "04-rejected-detail.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to approval queue" }).click();
  await rowFor(page, "Cascade Consulting")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(
    staleRequestUrl.replace("/employee/", "/manager/"),
  );
  const requestId = staleRequestUrl.split("/").at(-1);
  const cookies = await context.cookies("http://localhost:8080");
  const csrf = cookies.find((cookie) => cookie.name === "XSRF-TOKEN");
  expect(csrf).toBeDefined();
  let concurrentDecisionStatus = 0;
  let concurrentDecisionState = "";
  await page.route(
    "**/api/v1/requests/*/reject",
    async (route) => {
      const concurrentDecision = await context.request.post(
        `http://localhost:8080/api/v1/requests/${requestId}/approve`,
        {
          data: { expected_version: 1 },
          headers: { "X-XSRF-TOKEN": decodeURIComponent(csrf!.value) },
        },
      );
      concurrentDecisionStatus = concurrentDecision.status();
      concurrentDecisionState = (await concurrentDecision.json()).status;
      await route.continue();
    },
    { times: 1 },
  );
  const conflictResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/reject"),
  );
  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await page
    .getByLabel("Rejection reason")
    .fill("This stale decision must not persist.");
  await page.getByRole("button", { name: "Confirm rejection" }).click();
  const conflictResponse = await conflictResponsePromise;
  expect(concurrentDecisionStatus).toBe(200);
  expect(concurrentDecisionState).toBe("finance_review");
  expect(conflictResponse.status()).toBe(409);
  expect((await conflictResponse.json()).error.code).toBe("STATE_CONFLICT");
  await expect(page.locator(".alert.error")).toContainText(
    "already been decided",
  );
  await expect(page.getByText("Finance review", { exact: true })).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "05-stale-decision-conflict.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Back to approval queue" }).click();
  await page
    .getByRole("combobox", { name: "Status" })
    .selectOption("rejected");
  await expect(page).toHaveURL(/status=rejected/);
  await expect(page.getByRole("table")).toContainText("Beacon Operations");
  await expect(page.locator(".activity-card.pending")).toContainText("0");
  await expect(page.locator(".activity-card.approved")).toContainText("2");
  await expect(page.locator(".activity-card.rejected")).toContainText("1");
  await page.screenshot({
    path: path.join(evidenceDirectory, "06-rejected-filter.png"),
    fullPage: true,
  });

  await page
    .getByRole("combobox", { name: "Status" })
    .selectOption("manager_review");
  await expect(page.getByText("No requests awaiting review")).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "07-empty-default-queue.png"),
    fullPage: true,
  });

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, "manager2");
  await page.goto(approveRequestUrl.replace("/employee/", "/manager/"));
  await expect(
    page.getByRole("heading", { name: "Request unavailable" }),
  ).toBeVisible();
  await expect(page.getByText(/not assigned to you/i)).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "08-unassigned-request-404.png"),
    fullPage: true,
  });

  console.log(
    "manager_flow=pending:3->0 approved:0->2 rejected:0->1 stale_status=409 unassigned_status=404",
  );
});

async function login(page: Page, identifier: string) {
  if (!page.url().endsWith("/login")) {
    await page.goto("/login");
  }
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();
  await page.getByLabel("Login identifier").fill(identifier);
  await page.getByLabel("Password").fill("OrbisFlow123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(
    identifier.startsWith("manager")
      ? /\/manager\/queue$/
      : /\/employee\/requests$/,
    { timeout: 20_000 },
  );
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

function rowFor(page: Page, vendor: string) {
  return page.getByRole("row").filter({ hasText: vendor });
}
