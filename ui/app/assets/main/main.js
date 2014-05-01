/**
 * Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
require.config({
  paths: {
    jquery: 'vendors/jquery',
    ko: 'vendors/knockout',
    css: '../lib/require-css/css',
    text: '../lib/requirejs-text/text'
  }
});

var vendors = [
  'webjars!jquery',
  'webjars!knockout',
  'commons/visibility',
  '../lib/requirejs-text/text',
  '../lib/require-css/css'
]

var commons = [
  'commons/templates',
  'commons/effects',
  'commons/utils',
  'commons/settings',
  'commons/streams',
  'commons/events'
]

var services = [
    'services/sbt',
    'services/build',
    'services/log',
    'services/tutorial',
    'services/connection',
    'widgets/notifications/notifications',
    'services/typesafe'
]

var core = [
  'main/model',
  'main/view',
  'main/router',
  'main/pluginapi',
  'main/keyboard',
  'main/omnisearch',
  'main/navigation'
]

require(vendors, function($, ko) {
  window.ko = ko; // it's used on every page...
  require(commons, function() {
    require(services, function() {
      require(core, function(model,view, router) {
        view.render(model);
        router.load(window.location.hash)
      })
    })
  })
})
