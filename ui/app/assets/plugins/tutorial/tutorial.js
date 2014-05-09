/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "main/router",
  "services/tutorial",
  "text!./tutorial.html",
  "text!./tutorial-panel.html",
  "css!./tutorial",
  "css!widgets/navigation/menu"
], function(
  plugins,
  router,
  tutorialService,
  template,
  templatePanel
){

  var ifCurrentPlugin = function() {
    return window.location.hash.indexOf("#tutorial") == 0;
  }

  var TutorialState   = new function(){
    var self = this;

    this.hasTutorial  = tutorialService.hasTutorial;
    this.tutorial     = tutorialService.getTutorial(null);
    this.table        = tutorialService.getTable(null);
    this.page         = ko.observable(null);

    this.gotoPrevPage = function(__, e){
      if (e) {
        e.preventDefault();
        e.stopPropagation();
      }
      if (self.hasPrevPage())
        self.gotoPage(self.page().index-1);
    }
    this.gotoNextPage = function(__, e){
      if (e) {
        e.preventDefault();
        e.stopPropagation();
      }
      if (self.hasNextPage())
        self.gotoPage(self.page().index+1);
    }
    this.gotoPage = function(i){
      // Tutorial may not be loaded
      if (tutorialService.tutorialLoaded.state() == "resolved"){
        p = tutorialService.getPage(i);
        self.page(p);
        if (ifCurrentPlugin()){
          window.location.hash = "#tutorial/"+i;
        }
      } else {
        tutorialService.tutorialLoaded.then(function() {
          self.gotoPage(i);
        });
      }
    }

    this.hasPrevPage  = ko.computed(function(){
      if (self.page())
        return self.page().index != 0;
      else
        return false;
    });
    this.hasNextPage  = ko.computed(function(){
      if (self.page())
        return self.page().index != (self.table().length-1);
      else
        return false;
    });
  }

  return {

    render: function(url) {
      var $tutorial = $(template)[0];
      ko.applyBindings(TutorialState, $tutorial);
      return $tutorial;
    },

    renderPanel: function() {
      if (!TutorialState.page()) TutorialState.gotoPage(0);
      var $templatePanel = $(templatePanel)[0];
      ko.applyBindings(TutorialState, $templatePanel);
      return  $templatePanel;
    },

    // We can't use memorizeUrl because the panel can modify the url
    route: function(url, breadcrumb) {
      // TODO: Add introduction view
      if (url.parameters[0] === undefined || url.parameters[0] === "") {
        if (TutorialState.page()){
          router.redirect('tutorial/'+TutorialState.page().index)
        } else {
          // TutorialState.page(0); // No page
          TutorialState.gotoPage(0);
          breadcrumb([['tutorial/', "Tutorial"]]);
        }
      } else {
        TutorialState.gotoPage(url.parameters[0]);
        if (TutorialState.page())
          breadcrumb([['tutorial/', "Tutorial"],['tutorial/'+url.parameters[0], TutorialState.page().title]]);
      }
    }

    // TODO : Plugin this when keyboard is introduced
    // keyboard: function(key) {
    //   if (key == "TOP" && TutorialState.hasPrevPage()) {
    //     TutorialState.gotoPrevPage();
    //   } else if (key == "BOTTOM" && TutorialState.hasNextPage()) {
    //     TutorialState.gotoNextPage();
    //   }
    // }

  }

});
