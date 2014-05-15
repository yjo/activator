define(function() {

  var all = {};

  return {
    observable: function(label, def) {
      if (!all[label]) {
        try {
          var stored = JSON.parse(window.localStorage.getItem(label));
          var value = stored !== null ? stored : def;
          all[label] = ko.observable(value);
          debug && console.debug("[SETTINGS]:", label, value);
          all[label].subscribe(function(newValue) {
            window.localStorage[label] = JSON.stringify(newValue);
          });
          return all[label];
        } catch (e) {
          throw "Your localStorage may be compromised, remove them to continue: "+e;
        }
      } else {
        throw "Settings observable should be declared only once.";
      }
    }
  }

});
