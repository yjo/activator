/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(function() {

  // Talk to backend, update omnisearchOptions with result
  // TODO - Figure out a better way to get this URL!
  var doSearch = function(keywords){
    var url = '/app/' + window.serverAppModel.id + '/search/' + keywords;
    return $.ajax({
     url: url,
     dataType: 'json'
    });
  }

  return {
    doSearch: doSearch
  }
});
