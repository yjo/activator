/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(["text!./viewImage.html", 'commons/utils', 'commons/widget'], function(template, utils, Widget){

  var ImageView = utils.Class(Widget, {
    id: 'code-image-view',
    template: template,
    init: function(args) {
      var self = this;
      self.file = args.file;
      self.fileLoadUrl = ko.computed(function() {
        var file = self.file();
        return '/api/local/show?location=' + file.location;
      });
    },
    scrollToLine: function(line) {
    }
  });
  return ImageView;
});
