/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/header/header',
  'widgets/navigation/navigation',
  'widgets/layout/layout',
  'widgets/panels/panels'
], function(
  header,
  navigation,
  layout,
  panels
) {

  // Get the internal states from navigation and pannels
  // since they influence the main view and the header
  var ViewState = {
    navigation: navigation.state,
    panel: panels.state
  }

  return {
    render: function() {

      ko.applyBindings(ViewState, document.body); //Body tag also has bindings

      $(document.body)
        .append(navigation.render())
        .append(header.render(ViewState))
        .append(layout.render())
        .append(panels.render());
    }
  }

});
