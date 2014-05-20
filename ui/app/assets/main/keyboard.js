// this file is a simple keymage wrapper to control our usage of it
// and offer some convenience functionality.
define([ 'lib/keymage/keymage' ], function(key) {
  return {
    installBindingsInScope: function(scope, keybindings) {
      $.each(keybindings, function(i, params) {
        // we need to decide if there's a sub-scope in the parameter list,
        // which would look like key('scope', 'ctrl-c', function(){})
        var adjusted = null;
        if (params.length > 2 && typeof(params[2]) == 'function') {
          adjusted = params.slice(0);
          adjusted[0] = scope + '.' + params[0];
        } else {
          adjusted = params.slice(0);
          adjusted.unshift(scope);
        }
        debug && console.log("in scope " + scope + " creating keybinding ", adjusted);
        key.apply(null, adjusted);
      });
    },
    pushScope: function(scope) {
      debug && console.log("pushing scope " + scope);
      key.pushScope(scope);
      debug && console.log("key scope is now: " + key.getScope());
    },
    popScope: function(scope) {
      debug && console.log("popping scope " + scope);
      key.popScope(scope);
      debug && console.log("key scope is now: " + key.getScope());
    }
  };
});
