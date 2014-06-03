/**
 * Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
require.config({
  baseUrl:  '/public',
  paths: {
    jquery: 'lib/jquery//jquery',
    ko: 'lib/knockout/knockout',
    ace: 'lib/ace/src/ace'
  }
});

var vendors = [
  'lib/jquery/jquery',
  'lib/knockout/knockout',
  'css',
  'text',
  'lib/ace/src/ace',
  'commons/visibility'
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
    'services/oldSbt',
    'services/sbt',
    'services/build',
    'services/log',
    'services/tutorial',
    'services/connection',
    'widgets/notifications/notifications',
    'services/typesafe'
]

var core = [
  'main/view',
  'main/router',
  'main/keyboard'
]

require(vendors, function($, ko) {
  window.ko = ko; // it's used on every page...
  require(commons, function() {
    require(services, function() {
      require(core, function(view, router) {
        view.render();
        router.load(window.location.hash)
      })
    })
  })
})
