#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Injection build-time de la version automatique dans la page NoVaY.

La page officielle de telechargement est concue et maintenue par
l owner dans JCVERSA/novay-page. Le workflow de p la clone et la
construit ; ce script injecte dans index.html, AVANT le build Vite :

  - un span de metadonnees dans la ligne download-note du hero
    (meme style que "Android 7+ - Gratuit - APK universel") ;
  - un script inline qui interroge la derniere release publique
    (API GitHub) et remplit ce span ("Version X.Y.Z - 11,95 Mo")
    plus les infobulles des liens APK (date, 7z, SHA-256).

Le script echoue BRUYAMMENT si les ancres ont disparu (redesign de
novay-page) : mieux un echec visible qu'une page livree sans version.

Idempotent : sans effet si l injection est deja presente.
"""

import io
import sys

SPAN_ANCHOR = """<span>APK universel</span>
          </div>"""

SPAN_NEW = """<span>APK universel</span>
            <span aria-hidden="true">\u00b7</span>
            <span id="novay-release-meta" hidden></span>
          </div>"""

VERSION_SCRIPT = r"""
<!-- Version automatique (injectee au build par le workflow de p) :
     interroge la derniere release publique. Repli silencieux si l
     API est injoignable - les liens restent exacts (URL permanente
     releases/latest). -->
<script>
(function () {
  "use strict";
  function fill() {
    fetch("https://api.github.com/repos/JCVERSA/p/releases/latest", {
      headers: { Accept: "application/vnd.github+json" },
    })
      .then(function (r) {
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then(function (rel) {
        var meta = document.getElementById("novay-release-meta");
        var version = (rel.tag_name || "").replace(/^v/, "");
        var apkSize = "";
        var zSize = "";
        var assets = rel.assets || [];
        for (var i = 0; i < assets.length; i++) {
          if (assets[i].name === "novay-universal-release.apk") {
            apkSize = " \u00b7 " + (assets[i].size / 1048576).toFixed(2).replace(".", ",") + " Mo";
          } else if (assets[i].name === "novay-universal-release.7z") {
            zSize = "7z : " + (assets[i].size / 1048576).toFixed(2).replace(".", ",") + " Mo";
          }
        }
        if (meta && version) {
          meta.textContent = "Version " + version + apkSize;
          meta.hidden = false;
        }
        var date = rel.published_at
          ? new Date(rel.published_at).toLocaleDateString("fr-FR", {
              day: "numeric",
              month: "long",
              year: "numeric",
            })
          : "";
        var sha = /([0-9a-f]{64})\s+novay-universal-release\.apk/i.exec(rel.body || "");
        var titre = [];
        if (version) titre.push("Version " + version);
        if (date) titre.push("publi\u00e9e le " + date);
        if (zSize) titre.push(zSize);
        if (sha) titre.push("SHA-256 : " + sha[1]);
        if (titre.length) {
          var liens = document.querySelectorAll(
            'a[href*="novay-universal-release.apk"]'
          );
          for (var j = 0; j < liens.length; j++) {
            liens[j].setAttribute("title", titre.join(" \u00b7 "));
          }
        }
      })
      .catch(function () {
        /* silencieux : la page reste exacte sans l API */
      });
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", fill);
  } else {
    fill();
  }
})();
</script>
"""


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "index.html"
    source = io.open(path, encoding="utf-8").read()

    if 'id="novay-release-meta"' in source:
        print("injection deja presente - rien a faire")
        return 0

    if source.count(SPAN_ANCHOR) != 1:
        print(
            "::error::ancre download-note introuvable ou ambigue dans "
            + path
            + " - la page novay-page a change, adapter inject_release_meta.py"
        )
        return 1
    if source.count("</body>") != 1:
        print("::error::ancre </body> ambigue dans " + path)
        return 1

    source = source.replace(SPAN_ANCHOR, SPAN_NEW, 1)
    source = source.replace(
        "</body>", VERSION_SCRIPT.rstrip("\n") + "\n</body>", 1
    )
    io.open(path, "w", encoding="utf-8").write(source)
    print("version automatique injectee dans " + path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
