// Kleiner Hinweis auf die Original-Map und zurück zur Roadmap.
(function () {
  var box = document.createElement("div");
  box.innerHTML =
    'Map: <a href="https://www.planetminecraft.com/project/the-apothecary-diaries-map-6765654/" target="_blank" rel="noopener">The Apothecary Diaries</a> (Planet Minecraft)' +
    ' · <a href="../">Roadmap</a>';
  box.style.cssText =
    "position:fixed;right:8px;bottom:8px;z-index:10000;padding:6px 10px;" +
    "font:12px/1.4 system-ui,sans-serif;color:#fff;background:rgba(0,0,0,.6);" +
    "border-radius:6px;max-width:calc(100vw - 16px)";
  var links = box.getElementsByTagName("a");
  for (var i = 0; i < links.length; i++) links[i].style.color = "#9cf";
  document.body.appendChild(box);
})();
