/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./notifications.html', 'webjars!knockout', 'commons/widget', 'commons/utils', 'services/build'],
    function(template, ko, Widget, utils, build) {

  var Notifications = utils.Class(Widget, {
    id: 'notifications-widget',
    template: template,
    init: function() {
      this.count = ko.computed(function() { return build.errors.all().length; });
      this.items = build.errors.all;
    }
  });

  return new Notifications();
});
