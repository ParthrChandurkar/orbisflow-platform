import { expect, test, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const evidenceDirectory = path.resolve(
  process.cwd(),
  "../docs/evidence/stage-21",
);
const invoices = {
  northstar:
    process.env.STAGE21_NORTHSTAR_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage21-northstar.png"),
  meridian:
    process.env.STAGE21_MERIDIAN_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage21-meridian.png"),
  harbor:
    process.env.STAGE21_HARBOR_INVOICE_PATH ??
    path.join(process.env.TEMP ?? ".", "orbisflow-stage21-harbor.png"),
};

test.beforeAll(async () => {
  await mkdir(evidenceDirectory, { recursive: true });
});

test("captures polished three-role desktop and mobile workspaces", async ({
  page,
}) => {
  await login(page, "employee1");
  await expect(page.getByText("No requests yet")).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "01-employee-empty.png"),
    fullPage: true,
  });

  const northstarUrl = await uploadInvoice(page, invoices.northstar);
  await page.getByRole("link", { name: "Back to requests" }).click();
  await uploadInvoice(page, invoices.meridian);
  await page.getByRole("link", { name: "Back to requests" }).click();
  await uploadInvoice(page, invoices.harbor);
  await page.getByRole("link", { name: "Back to requests" }).click();
  await expect(page.getByRole("table")).toContainText("Northstar Office");
  await expect(page.getByRole("table")).toContainText("Meridian Supply");
  await expect(page.getByRole("table")).toContainText("Harbor Systems");
  await page.screenshot({
    path: path.join(evidenceDirectory, "02-employee-populated.png"),
    fullPage: true,
  });

  await rowFor(page, "Northstar Office")
    .getByRole("link", { name: /View/ })
    .click();
  await expect(page).toHaveURL(northstarUrl);
  await expect(page.getByRole("heading", { name: "Northstar Office" })).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "03-request-detail.png"),
    fullPage: true,
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/employee/requests");
  await expect(page.getByText("Northstar Office")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(
    await page.locator(".request-table thead").evaluate(
      (element) => getComputedStyle(element).display,
    ),
  ).toBe("none");
  await page.screenshot({
    path: path.join(evidenceDirectory, "06-mobile-employee-dashboard.png"),
    fullPage: true,
  });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await logout(page);
  await login(page, "manager1");
  await expect(page.getByRole("table")).toContainText("Northstar Office");
  await expect(page.locator(".activity-card.pending")).toContainText("3");
  await page.screenshot({
    path: path.join(evidenceDirectory, "04-manager-queue.png"),
    fullPage: true,
  });

  await approveFromQueue(page, "Northstar Office");
  await approveFromQueue(page, "Meridian Supply");
  await logout(page);
  await login(page, "finance1");
  await expect(page.getByRole("table")).toContainText("Northstar Office");
  await expect(page.getByRole("table")).toContainText("Meridian Supply");
  await expect(page.getByRole("table")).not.toContainText("Harbor Systems");
  await page.screenshot({
    path: path.join(evidenceDirectory, "05-finance-queue.png"),
    fullPage: true,
  });

  console.log(
    "ui_polish=employee_empty+populated detail manager_queue finance_queue mobile_no_overflow",
  );
});

async function login(page: Page, identifier: string) {
  await page.goto("/login");
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
  await expect(page.getByText("Manager review", { exact: true })).toBeVisible({
    timeout: 90_000,
  });
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
  expect((await responsePromise).status()).toBe(200);
}

function rowFor(page: Page, vendor: string) {
  return page.getByRole("row").filter({ hasText: vendor });
}
