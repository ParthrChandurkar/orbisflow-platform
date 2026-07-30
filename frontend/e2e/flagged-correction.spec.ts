import { expect, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const evidenceDirectory = path.resolve(
  process.cwd(),
  "../docs/evidence/stage-18",
);
const invoicePath =
  process.env.STAGE18_FLAGGED_INVOICE_PATH ??
  path.join(
    process.env.TEMP ?? ".",
    "orbisflow-stage18-flagged-invoice.png",
  );

test.beforeAll(async () => {
  await mkdir(evidenceDirectory, { recursive: true });
});

test("employee corrects and resubmits a flagged extraction", async ({
  page,
}) => {
  await page.goto("/login");
  await page.getByLabel("Login identifier").fill("employee1");
  await page.getByLabel("Password").fill("OrbisFlow123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/employee\/requests$/, { timeout: 20_000 });

  await page.getByRole("link", { name: "Submit invoice" }).first().click();
  await page.locator('input[type="file"]').setInputFiles(invoicePath);
  await page.getByRole("button", { name: "Submit invoice" }).click();
  await expect(page).toHaveURL(/\/employee\/requests\/[0-9a-f-]+$/, {
    timeout: 30_000,
  });

  await expect(
    page.getByText("Needs correction", { exact: true }),
  ).toBeVisible({ timeout: 90_000 });
  await expect(
    page.getByText("Invoice details need attention", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Total amount is required.", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "05-flagged-validation.png"),
    fullPage: true,
  });

  await page.getByLabel("Total amount").fill("150.00");
  const patchResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "PATCH" &&
      response.url().endsWith("/extracted-data"),
  );
  await page.getByRole("button", { name: "Save corrections" }).click();
  const patchResponse = await patchResponsePromise;
  expect(patchResponse.status()).toBe(200);
  expect(patchResponse.request().postDataJSON()).toMatchObject({
    expected_version: 1,
    total_amount: "150.00",
  });
  const patchBody = await patchResponse.json();
  expect(patchBody.version).toBeGreaterThan(1);
  expect(patchBody.extracted_data.validation_flags).toEqual([]);
  await expect(
    page.getByText("Total amount is required.", { exact: true }),
  ).toBeHidden();
  await expect(page.getByLabel("Total amount")).toHaveValue("150");
  await page.screenshot({
    path: path.join(evidenceDirectory, "06-correction-saved.png"),
    fullPage: true,
  });

  const resubmitResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/resubmit"),
  );
  await page
    .getByRole("button", { name: "Resubmit for approval" })
    .click();
  const resubmitResponse = await resubmitResponsePromise;
  expect(resubmitResponse.status()).toBe(200);
  const resubmitBody = await resubmitResponse.json();
  expect(resubmitBody.status).toBe("manager_review");
  expect(resubmitResponse.request().postDataJSON()).toEqual({
    expected_version: patchBody.version,
  });

  await expect(
    page.getByText("Manager review", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", {
      name: "Northwind Services",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.getByText("field correction", { exact: true })).toBeVisible();
  await expect(
    page
      .getByRole("listitem")
      .filter({ hasText: "employee review → manager review" })
      .getByText("routing", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: path.join(evidenceDirectory, "07-corrected-resubmitted.png"),
    fullPage: true,
  });

  console.log(
    `flag=MISSING_TOTAL_AMOUNT patch_version=${patchBody.version} final_status=${resubmitBody.status}`,
  );
});
