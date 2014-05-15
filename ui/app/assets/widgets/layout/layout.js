define([
  "text!./layout.html",
  "css!./layout"
], function(
  template
){

  $layout = $(template)[0];
  return {
    render: function() {
      return $layout
    }
  }

});
