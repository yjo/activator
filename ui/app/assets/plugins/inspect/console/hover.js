/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(function() {


  var offset = { 'left': 2, 'top': 2, 'right': 10, 'bottom': 15 };

  ko.bindingHandlers.hoverPopup = {
    init: function(element, valueAccessor) {
      var popupClass = ko.utils.unwrapObservable(valueAccessor()) || 'hoverPopup';

      var $element = $(element);

      var popup = $('<div></div>').addClass(popupClass).hide().appendTo($element);

      var visible = false;

      function show() {
        if (!visible) {
          popup.show();
          visible = true;
        }
      }

      function hide() {
        if (visible) {
          popup.hide();
          visible = false;
        }
      }

      function position(event) {
        var mouseX = event.pageX - $element.offset().left + $element.scrollLeft();
        var mouseY = event.pageY - $element.offset().top + $element.scrollTop();
        var left = mouseX + offset.right;
        var top = mouseY + offset.bottom;
        var width = popup.innerWidth();
        var height = popup.innerHeight();
        if (left > ($element.width() - width)) left = mouseX - width - offset.left;
        if (top > ($element.height() - height)) top = mouseY - height - offset.top;
        return { 'left': left, 'top': top };
      }

      $element.on('mousemove', function(e) {
        var text = $(e.target).data('hoverText');
        if (text) {
          popup.text(text);
          popup.css(position(e));
          show();
        } else {
          hide();
        }
      });

      $element.mouseleave(function(e) {
        hide();
      });
    }
  };
});
