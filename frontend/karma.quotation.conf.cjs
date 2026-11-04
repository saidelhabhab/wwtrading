module.exports = function configureQuotationTests(config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    reporters: ['progress'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    customLaunchers: {
      EdgeHeadlessQuotation: {
        base: 'ChromeHeadless',
        flags: [
          '--disable-gpu',
          '--disable-software-rasterizer',
          '--no-sandbox'
        ]
      }
    }
  });
};
