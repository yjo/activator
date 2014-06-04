/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['services/search', 'services/sbt'], function(search, sbt) {

  var searchString = ko.observable("");
  var searchStringLast = "";
  var busy = ko.observable(false);
  var active = ko.observable(false);
  var options = ko.observableArray([]);
  var selected = ko.observable(0);

  var combinedSearch = function(keywords) {
    console.log("starting search on " + keywords);
    return ($.when(search.doSearch(keywords), sbt.possibleAutocompletions(keywords))
    .then(function(searchValues, sbtCompletions) {
        // TODO not handling errors here...
        var sbtValues = $.map(sbtCompletions[0].choices, function(completion, i) {
          return {
            title: completion.display,
            subtitle: "run sbt task " + completion.display,
            type: "Sbt",
            url: false,
            execute: keywords + completion.append
          };
        });
        var values = sbtValues.concat(searchValues[0]);
        return values;
    }));
  };

  var activate = function(item) {
    if (item.url) {
      location.href = item.url;
    } else if (item.execute) {
      sbt.requestExecution(item.execute);
    }
  }

  var onKeyUp = function(data, event){
    switch (event.keyCode) {
      // Escape
      case 27:
        event.target.blur();
        break;
      // Return
      case 13:
        var selectedItem = options()[selected()];
        if (selectedItem) {
          activate(selectedItem);
          event.target.blur();
        }
        break;
      // Up
      case 38:
        if (selected() > 0) {
          selected(selected() - 1);
        } else {
          selected(options().length - 1);
        }
        scrollToSelected();
        break;
      // Down
      case 40:
        if (selected() < options().length - 1) {
          selected(selected() + 1);
        } else {
          selected(0);
        }
        scrollToSelected();
        break;
      default:
        var keywords = searchString();
        // Don't search until at least two characters are entered and search string isn't the same as last
        if (keywords.length >= 2 && keywords != searchStringLast) {
          busy(true);
          combinedSearch(keywords)
          .done(function(values) {
            // No values returned
            if (values.length == 0) {
              values = [{
                title: "(no results found)",
                subtitle: "",
                type: "",
                url: false
              }];
            }
            // TODO - Maybe be smarter about how we fill stuff out here?
            options(values);
            busy(false);
            active(true);
            searchStringLast = keywords;
          });
        } else {
          busy(false);
          active(false);
        }
        break;
    }
    return true;
  }
  var scrollToSelected = function() {
    var $omnisearch = $('#omnisearch ul');
    var $selected = $omnisearch.find('li.selected');
    if ($selected.position().top < 0) {
      $omnisearch.scrollTop($omnisearch.scrollTop() + $selected.position().top);
    } else if ($selected.position().top + $selected.outerHeight() >= $omnisearch.height()) {
      $omnisearch.scrollTop($omnisearch.scrollTop() + $selected.position().top + $selected.outerHeight() - $omnisearch.height());
    }
  }
  var onOptionSelected = function(data){
    var self = this;
    if (data) {
      activate(data);
    }
  }
  var onBlur = function(data, event){
    var self = this;
    // Delay hiding of omnisearch list to catch mouse click on list before it disappears
    setTimeout(function(){
      active(false);
      selected(0);
      searchString("");
    }, 100);
  }
  var openSearch = function() {
    $("#omnisearch input")[0].focus();
    // TODO nothing connects to this?
    $.event.trigger("search.open");
  }

  return {
    onKeyUp: onKeyUp,
    scrollToSelected: scrollToSelected,
    onOptionSelected: onOptionSelected,
    onBlur: onBlur,
    openSearch: openSearch,
    searchString: searchString,
    searchStringLast: searchStringLast,
    busy: busy,
    active: active,
    options: options,
    selected: selected
  }
});
