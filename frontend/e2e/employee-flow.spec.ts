import { expect, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const evidenceDirectory = path.resolve(
  process.cwd(),
  "../docs/evidence/stage-18",
);
const invoicePath =
  process.env.STAGE18_INVOICE_PATH ??
  path.join(process.env.TEMP ?? ".", "orbisflow-stage18-invoice.png");

test.beforeAll(async () => {
  await mkdir(evidenceDirectory, { recursive: true });
});

test("employee completes the real upload and extraction journey", async ({
  page,
}) => {
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "01-login.png"),
    fullPage: true,
  });

  await page.getByLabel("Login identifier").fill("employee1");
  await page.getByLabel("Password").fill("OrbisFlow123!");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/employee\/requests$/, { timeout: 20_000 });
  await expect(page.getByText("No requests yet")).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "02-empty-dashboard.png"),
    fullPage: true,
  });

  await page.getByRole("link", { name: "Submit invoice" }).first().click();
  await expect(
    page.getByRole("heading", { name: "Submit an invoice" }),
  ).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles(invoicePath);
  await page.getByRole("button", { name: "Submit invoice" }).click();

  await expect(page).toHaveURL(/\/employee\/requests\/[0-9a-f-]+$/, {
    timeout: 30_000,
  });
  const polling = page.getByText(/refreshes every 3 seconds/i);
  const pollingObserved = await polling
    .waitFor({ state: "visible", timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  await expect(
    page.getByText("Manager review", { exact: true }),
  ).toBeVisible({ timeout: 90_000 });
  await expect(
    page.getByRole("heading", { name: "Acme Consulting", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Strategy workshop")).toBeVisible();
  await expect(page.getByText("Research report")).toBeVisible();
  await expect(page.getByText("Audit timeline")).toBeVisible();
  await expect(page.getByText("upload", { exact: true })).toBeVisible();
  await expect(page.getByText("routing", { exact: true })).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "03-extracted-request.png"),
    fullPage: true,
  });
  console.log(`polling_observed=${pollingObserved}`);

  await page.getByRole("link", { name: /Back to requests/ }).click();
  await expect(
    page.getByRole("cell").filter({ hasText: "Acme Consulting" }),
  ).toBeVisible();
  await expect(
    page.getByRole("table").getByText("Manager review", { exact: true }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "04-logout-redirect.png"),
    fullPage: true,
  });
});

test("a mid-session invalid JWT redirects globally to login", async ({
  context,
  page,
}) => {
  await page.goto("/login");
  await page.getByLabel("Login identifier").fill("employee1");
  await page.getByLabel("Password").fill("OrbisFlow123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/employee\/requests$/, { timeout: 20_000 });
  await expect(page.getByText("My invoice requests")).toBeVisible();

  await context.addCookies([
    {
      name: "ORBIS_SESSION",
      value: "tampered-mid-session-token",
      domain: "localhost",
      path: "/api",
      httpOnly: true,
      secure: false,
      sameSite: "Strict",
    },
  ]);
  await page
    .getByRole("combobox", { name: "Status", exact: true })
    .selectOption("processed");

  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await expect(
    page.getByRole("heading", { name: "Sign in to your workspace" }),
  ).toBeVisible();
  console.log(`global_401_redirect_url=${page.url()}`);
});
