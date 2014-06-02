define([
  "./omnisearch",
  'widgets/notifications/notifications',
  "text!./header.html",
  "css!./header"
], function(
  omnisearch,
  notifications,
  template
){

  $header = $(template)[0];

  return {
    render: function(ViewState) {
      // Include other states to the view State
      ViewState.omnisearch = omnisearch;
      ViewState.notifications = notifications;

      ko.applyBindings(ViewState, $header);

      return $header
    }
  }

});
