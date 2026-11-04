const os = require('node:os');
const path = require('node:path');

module.exports = function configureClientsTests(config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    reporters: ['progress'],
    browserDisconnectTimeout: 10000,
    browserNoActivityTimeout: 30000,
    captureTimeout: 30000,
    retryLimit: 0,
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    customLaunchers: {
      EdgeHeadlessClients: {
        base: 'Chrome',
        flags: [
          '--headless=new',
          '--disable-gpu',
          '--no-sandbox',
          '--disable-dev-shm-usage',
          `--user-data-dir=${path.join(os.tmpdir(), `wwtrading-karma-clients-${process.pid}`)}`
        ]
      }
    }
  });
};

