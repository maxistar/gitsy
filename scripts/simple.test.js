import { expect, test } from "vitest";
import { remote } from "webdriverio";

test("Shows version on about dialog", async () => {

  const capabilities = {
    platformName: "Android",
    "appium:automationName": "UiAutomator2",
    //  'appium:deviceName': 'emulator-5554',
    "appium:autoGrantPermissions": true,
    "appium:noReset": true
  };

  const wdOpts = {
    hostname: process.env.APPIUM_HOST || "localhost",
    port: parseInt(process.env.APPIUM_PORT, 10) || 4723,
    logLevel: "info",
    capabilities,
  };


  const driver = await remote(wdOpts);

  await driver.startActivity("me.maxistar.gitsync", "me.maxistar.gitsync.MainActivity");

  const el1 = await driver.$("id:com.maxistar.textpad:id/editText1");
  const el2 = await driver.$("accessibility id:More options");
  await el2.click();
  const el3 = await driver.$(
    'xpath://android.widget.TextView[@resource-id="me.maxistar.gitsync:id/title" and @text="About"]'
  );
  await el3.click();

  const aboutLink = await driver.$('//android.widget.TextView[@resource-id="me.maxistar.gitsync:id/app_info"]');
  expect(await aboutLink.getText()).toBe('Android GitSync\nVersion 0.0.1');

//  const conditionsLink = await driver.$('//android.widget.TextView[@resource-id="me.maxistar.gitsync:id/app_website"]');
//  await conditionsLink.click();

  const closeButton = await driver.$('//android.widget.Button[@resource-id="android:id/button1"]');
  await closeButton.click();

});
