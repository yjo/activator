define(function() {

  var pages = ko.observableArray([]);
  var table = ko.observableArray([]);
  var hasTutorial = ko.observable(false);
  var tutorialLoaded = $.when($.ajax("tutorial/index.html")).then(function(data){
    // parseHTML dumps the <html> <head> and <body> tags
    // so we'll get a list with <title> some <div> and some text nodes
    hasTutorial(true);
    var htmlNodes = $.parseHTML(data);
    $(htmlNodes).filter("div").each(function(i,el){
      $("a", el).each(function(j, link) {
        // Open external links in new window.
        if (link.getAttribute('href').indexOf("http://") == 0 && !link.target){
          link.target = "_blank";
        // Force shorcut class on links to code
        } else if (link.getAttribute('href').indexOf("#code/") == 0){
          $(link).addClass("shorcut");
        }
      });
      $("a", el).each(function(j, link) {
      });
      var title = $("h2", el).remove().html() || $(el).text().substring(0,40) + "...";
      pages.push({ index: i, title: title, page: el.innerHTML });
      table.push(title);
    });
  }, function(){
    hasTutorial(false);
    debug && console.log("Can't load the tutorial.");
  });

  return {

    hasTutorial: hasTutorial,
    tutorialLoaded: tutorialLoaded,
    // todo
    getTutorial: function() {
      return {
        name: "Tutorial Name",
        description: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras vitae lorem at neque mollis viverra eu eu tortor. Vivamus at viverra risus. Quisque scelerisque felis purus, a tempor elit vulputate tempor. Sed pharetra condimentum elementum. Aliquam lobortis, metus ut luctus commodo, neque justo cursus diam, eu semper augue dui a erat. Praesent eget augue dignissim, aliquet erat lobortis, feugiat dui.",
        source: "http://github.com/johndoe/activator-akka-spray",
        author: {
          name: "John Does",
          twitter: "johndoe"
        },
        partner: {
          url: "http://partner.com",
          logo: "http://dommkopfq6m1m.cloudfront.net/public/1387589794721/images/partners/svcs/chariot.png",
          summary: "Maecenas lorem arcu, tristique ut accumsan ac, ultricies non nulla. Pellentesque adipiscing venenatis risus at faucibus. In vehicula fermentum enim et placerat."
        }
      };
    },

    getTable: function() {
      return table;
    },

    getPage: function(number) {
      return pages()[number];
    }

  }
});
