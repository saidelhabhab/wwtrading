// Karma configuration — generated for running unit tests on machines without
// Chrome installed. Uses Edge (Chromium) via CHROME_BIN, or Chrome when present.
process.env.CHROME_BIN =
  process.env.CHROME_BIN ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

module.exports = function (config) {
  config.set({
    basePath: "",
    frameworks: ["jasmine", "@angular-devkit/build-angular"],
    plugins: [
      require("karma-jasmine"),
      require("karma-chrome-launcher"),
      require("karma-jasmine-html-reporter"),
      require("karma-coverage"),
      require("@angular-devkit/build-angular/plugins/karma"),
    ],
    customLaunchers: {
      EdgeHeadless: {
        base: "ChromeHeadless",
        flags: ["--no-sandbox", "--disable-gpu"],
      },
    },
    browsers: ["EdgeHeadless"],
    browserDisconnectTimeout: 10000,
    browserNoActivityTimeout: 60000,
    captureTimeout: 60000,
    restartOnFileChange: true,
    reporters: ["progress", "kjhtml"],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    singleRun: false,
  });
};
