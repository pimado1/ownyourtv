// English is written in the pages themselves; French replaces it when the browser (or the FR button) asks for it.
var FR = {
  nav_how: "Comment ça marche", nav_tvs: "Quelles TV", nav_faq: "Questions",
  h1: "Tu l'as payée. <em>Elle est à toi.</em>",
  lead: "Coupe les pubs et le pistage installés d'usine sur ta TV Android TV ou Google TV, et donne-lui un accueil qui te ressemble. À la télécommande, sans ordinateur.",
  code_label: "Sur ta TV, dans l'appli Downloader, tape",
  copy: "Copier", copied: "Copié", cta_how: "Voir les 3 étapes", cta_apk: "Télécharger l'APK",
  f_models: "modèles de TV reconnus", f_langs: "langues", f_undo: "bouton remet tout comme avant",
  ba_alt: "Avant : l'accueil Google TV d'origine avec une pub plein écran. Après : l'accueil OwnYourTV avec un ciel de nuit, la météo et les applis en cartes 3D.",
  before: "Avant : l'accueil d'origine, les pubs d'abord", after: "Après : OwnYourTV",
  how_eye: "Comment ça marche", how_h: "Trois étapes, tout à la télécommande",
  how_sub: "Une dizaine de minutes. Rien n'est supprimé : les applis sont désactivées, et un bouton les rallume.",
  s1_h: "Active le mode développeur",
  s1_p: "Paramètres › Système › À propos : clique 7 fois sur <kbd>Build de l'OS Android TV</kbd>. Puis Paramètres › Système › Options pour les développeurs : active <kbd>Débogage USB</kbd>.",
  s2_h: "Installe avec Downloader",
  s2_p: "Installe l'appli gratuite <kbd>Downloader</kbd> depuis le Play Store de ta TV, tape le code <kbd>8225348</kbd>, puis installe OwnYourTV.",
  s3_h: "Suis l'assistant",
  s3_p: "Accepte une fois la demande de débogage. L'assistant reconnaît ta TV, te propose le nettoyage recommandé, le nouvel accueil et ton dock. Puis un appui : c'est fait.",
  off_eye: "Ce qui est coupé", off_h: "Tout ce que tu n'as jamais demandé",
  t1_b: "Les pubs", t1_s: "Promos, rangées sponsorisées et démos de magasin mises par le fabricant.",
  t2_b: "Le pistage", t2_s: "Télémétrie, identifiants publicitaires et rapports d'usage.",
  t3_b: "Le superflu", t3_s: "Les restes que personne n'utilise sur une TV.",
  t4_b: "Jamais tes applis", t4_s: "Netflix, YouTube, Prime Video et les autres ne sont jamais touchées. Ce dont la TV a besoin pour démarrer, lire une chaîne ou une entrée HDMI n'est jamais proposé.",
  clean_alt: "L'étape de nettoyage de l'assistant : Recommandé, ou je choisis moi-même",
  clean_cap: "Recommandé, en un appui, ou les listes une par une.",
  home_eye: "Un accueil bien à elle", home_h: "Un ciel de nuit vivant, tes applis devant",
  h1_b: "Une aurore aux couleurs de ton appli", h1_s: "Choisis Netflix et le ciel devient rouge. Un ciel calme, qui ne bouge que quand tu bouges.",
  h2_b: "Ton dock, dans ton ordre", h2_s: "Choisis les applis de l'accueil pendant l'assistant, change-les quand tu veux.",
  h3_b: "Réglages rapides sur la touche engrenage", h3_s: "Volume, image, son, sources, Wi-Fi et Bluetooth sans quitter l'accueil.",
  home_alt: "L'accueil OwnYourTV avec Netflix sélectionné et une aurore rouge", quick_alt: "Les réglages rapides", dock_alt: "Le choix des applis du dock",
  tvs_eye: "Quelles TV", tvs_h: "Android TV et Google TV, modèle par modèle",
  tvs_sub: "OwnYourTV reconnaît des centaines de modèles TCL, Sony, Xiaomi, Philips, Hisense et d'autres, grâce à des listes testées sur de vraies TV. Elle change l'accueil comme chaque modèle le demande, ou le laisse tranquille quand un modèle ne peut pas en prendre un autre sans risque. Ci-dessous, les TV que les utilisateurs d'OwnYourTV ont partagées depuis l'appli.",
  st_models: "modèles reconnus", st_brands: "marques", st_reports: "partagées par leurs propriétaires",
  tag_ok: "Fonctionne", tag_no: "Accueil refusé", tag_block: "Accueil bloqué par la TV",
  rep_none: "Aucune TV partagée pour l'instant.",
  rep_err: "La liste n'a pas pu se charger. Elle est aussi <a href=\"https://github.com/pimado1/ownyourtv/issues?q=label%3Atv-report\">sur GitHub</a>.",
  faq_eye: "Questions", faq_h: "Avant de commencer",
  q1: "Est-ce sans risque pour ma TV ?",
  a1: "Les applis sont désactivées, jamais supprimées, et seules celles vérifiées sur une vraie TV sont cochées. Sur un modèle reconnu, OwnYourTV garde ce dont ce modèle a besoin. Si son accueil n'arrive pas à démarrer, un accueil de secours avec tes applis prend le relais. « Tout remettre » dans l'appli rallume chaque appli coupée et ramène ton accueil d'origine.",
  q2: "Google Play Protect dit que l'appli est bloquée",
  a2: "Play Protect prévient pour toute appli installée hors du Play Store. Choisis « Plus de détails », puis « Installer quand même ». OwnYourTV est open source : tout le monde peut lire ce qu'elle fait sur GitHub.",
  q3: "L'appli me demande de reconnecter le Wi-Fi",
  a3: "Sur certaines Google TV (Xiaomi, entre autres), le réseau Wi-Fi est enregistré par l'appli de première installation de la TV. Changer d'accueil coupe cette appli, ce qui oublierait le réseau. L'assistant te guide : oublie le réseau, reconnecte-le, et l'accueil peut changer sans couper Internet.",
  q4: "Ma TV ne veut pas d'un autre accueil",
  a4: "Certains modèles n'acceptent que leur propre accueil. OwnYourTV les connaît et te le dit : les pubs et le pistage sont quand même coupés, et ton accueil actuel reste.",
  q5: "Je peux le faire depuis un ordinateur ?",
  a5: "Oui : <code>ownyourtv.py</code> fait la même chose depuis Windows, macOS ou Linux, avec adb. Voir <a href=\"https://github.com/pimado1/ownyourtv#from-a-computer\">From a computer</a> sur GitHub.",
  q6: "OwnYourTV collecte-t-elle des données ?",
  a6: "Non. Tout se passe sur la TV. À la fin, l'appli propose de partager le modèle de ta TV et ce qui a marché, anonymement, seulement si tu appuies sur Partager. Détails sur la <a href=\"privacy.html\">page confidentialité</a>.",
  q7: "Combien ça coûte ?", a7: "Rien. OwnYourTV est gratuite et open source, sous licence MIT.",
  ft_dl: "Télécharger", ft_privacy: "Confidentialité", ft_credits: "Crédits", ft_mit: "Gratuit et open source, licence MIT",
  // Privacy page
  p_title: "Confidentialité", p_lead: "OwnYourTV ne collecte rien. Voici exactement ce qui sort de ta TV, et quand.",
  p_h1: "Sur ta TV", p_t1: "L'appli se connecte au port de débogage de ta propre TV (127.0.0.1) pour couper des applis et changer l'accueil. Rien de cette connexion ne quitte la TV. Pas de compte, pas de publicité, pas de mesure d'audience.",
  p_h2: "La météo", p_t2: "Si tu choisis une ville pour la météo, son nom (pour la trouver) puis ses coordonnées sont envoyés à <a href=\"https://open-meteo.com\">Open-Meteo</a>, un service météo gratuit et sans compte. Sans ville choisie, rien n'est envoyé.",
  p_h3: "Le partage, seulement si tu appuies sur Partager", p_t3: "Au dernier écran, le bouton Partager envoie un rapport anonyme. L'écran affiche la ligne envoyée avant que tu appuies.",
  p_c1: "Ce qui est envoyé", p_c2: "Ce qui n'est jamais envoyé",
  p_r1: "Marque et nom de modèle donnés par Android (ex. TCL Smart TV Pro, G08)", p_n1: "Ton nom, ton compte Google, ton adresse e-mail",
  p_r2: "Versions d'Android et d'OwnYourTV", p_n2: "La liste de tes applis",
  p_r3: "TV reconnue ou non, ce qui s'est passé pour l'accueil, si l'étape Wi-Fi a été nécessaire", p_n3: "Ton numéro de série, ta position, ta ville",
  p_r4: "Combien d'applis ont été coupées, et lesquelles de la liste OwnYourTV n'ont pas pu l'être", p_n4: "Ton historique de visionnage",
  p_h4: "Où va le rapport", p_t4: "Il passe par un petit relais hébergé chez Cloudflare, qui ne garde rien : il vérifie le rapport et l'ajoute à une page publique sur GitHub, une par modèle de TV. Le relais voit l'adresse IP de la requête, comme tout serveur, et ne l'utilise que pour limiter à 5 rapports par minute, sans l'enregistrer. Le rapport devient public : c'est la liste des TV compatibles.",
  p_h5: "Supprimer un rapport", p_t5: "Le rapport ne contient rien qui te désigne. Si tu veux quand même qu'il disparaisse, ouvre une issue sur GitHub en indiquant le modèle.",
  p_h7: "Les mises à jour", p_t7: "Une fois par jour, OwnYourTV demande à GitHub (api.github.com) le numéro de sa dernière version. Rien sur ta TV n'est envoyé ; comme tout serveur, GitHub voit l'adresse IP de la requête. Si tu choisis de mettre à jour, la nouvelle version est téléchargée depuis GitHub.",
  p_h8: "Profils et mode enfant", p_t8: "Les profils, le code parent et le temps d'écran d'un enfant restent sur la TV et ne sont jamais envoyés. Pour garder un enfant dans ses applis, OwnYourTV utilise un service d'accessibilité d'Android qui lit quelle appli est à l'écran, jamais ce qu'elle affiche, et seulement quand un profil enfant est utilisé.",
  p_h6: "Le code est public", p_t6: "Tout ce qui est décrit ici se vérifie dans le code : <a href=\"https://github.com/pimado1/ownyourtv/blob/main/app/src/main/java/app/ownyourtv/Report.kt\">ce que l'appli envoie</a> et <a href=\"https://github.com/pimado1/ownyourtv/blob/main/relay/worker.js\">ce que fait le relais</a>.",
  after_alt: "Après : l'accueil OwnYourTV avec un ciel de nuit, la météo et les applis en cartes 3D.",
  before_alt: "Avant : l'accueil Google TV d'origine avec une pub plein écran et des recommandations.",
  before_chip: "Avant", after_chip: "Après", compare_aria: "Avant et après",
  ba_hint: "Fais glisser pour comparer : l'accueil Google TV d'origine, puis OwnYourTV.",
  no_pc: "Pas d'ordinateur", no_phone: "Pas de téléphone", no_ai: "Pas d'IA", yes_remote: "Juste la télécommande",
  f_pc: "ordinateur ou téléphone nécessaire",
  safe_eye: "Sûr par conception", safe_h: "Pensé pour que ta TV ne reste jamais bloquée",
  safe_sub: "Une TV, ça ne se réinitialise pas sur un coup de tête. OwnYourTV prévoit les mauvais jours.",
  sf1_h: "Désactivées, jamais supprimées", sf1_p: "Chaque appli reste sur la TV. Seules les applis vérifiées sur une vraie TV sont cochées, et ce dont la TV a besoin n'est jamais proposé.",
  sf2_h: "Après une mise à jour de la TV", sf2_p: "Les mises à jour rallument souvent les pubs. OwnYourTV le repère au démarrage et sur son accueil, et les recoupe en un appui.",
  sf3_h: "Un accueil de secours", sf3_p: "Si le nouvel accueil n'arrive pas à démarrer, un accueil simple avec toutes tes applis prend le relais. Jamais d'écran noir.",
  sf4_h: "Un bouton remet tout", sf4_p: "« Tout remettre » rallume chaque appli et ramène ton accueil d'origine.",
  q10: "Chacun, ou un enfant, peut-il avoir son propre accueil ?",
  a10: "Oui, depuis la version 1.8. Chaque profil a son dock et sa couleur, choisis avec une bulle au réveil de la TV. Un profil enfant ne montre que les applis que tu choisis, avec une heure de coucher, un temps par jour et un volume maximum. Quand le temps est écoulé, un écran « Bonne nuit » apparaît et la TV se met en veille. Un code parent à 4 chiffres lève les limites, et une appli ouverte par les réglages ou par la voix ramène l'enfant à son accueil. Ça se règle dans les Réglages rapides, rubrique Profils.",
  q9: "Comment avoir les nouvelles versions ?",
  a9: "Depuis la version 1.7, l'accueil te prévient quand une nouvelle version sort. Appuie sur Mettre à jour : OwnYourTV vérifie qu'elle est signée avec la même clé, puis Android te demande de confirmer. Tes choix et ton dock restent. Avant la 1.7, installe-la par-dessus l'ancienne avec le même code Downloader.",
  q8: "Une mise à jour de la TV a remis les pubs",
  a8: "Ça arrive : les fabricants rallument leurs applis pendant les mises à jour. OwnYourTV le repère au démarrage suivant et propose de les recouper en un appui. Active le débogage USB quand elle le demande, puis recoupe-le à la fin.",
  try_eye: "Sur une vraie TV", try_h: "L'accueil, en vrai",
  try_sub: "Filmé sur une vraie TV, en parcourant le dock à la télécommande. L'aurore prend la couleur de chaque appli.",
  try_hint: "Une TCL C89B sous Google TV 14, sans montage.",
  video_aria: "L'accueil OwnYourTV sur une vraie TV : le dock défile appli par appli et l'aurore prend la couleur de chacune.",
  bar_cta: "Installer",
  p_updated: "Mis à jour le 29 octobre 2026.", p_back: "← Retour à l'accueil"
};

var EN = {}, lang = "en";
function t(k) { return (lang === "fr" && FR[k]) || EN[k] || k; }

function apply(next) {
  lang = next;
  document.documentElement.lang = next;
  document.querySelectorAll("[data-i18n]").forEach(function (el) {
    var k = el.getAttribute("data-i18n");
    if (!(k in EN)) EN[k] = el.innerHTML;
    el.innerHTML = t(k);
  });
  document.querySelectorAll("[data-i18n-alt]").forEach(function (el) {
    var k = el.getAttribute("data-i18n-alt");
    if (!(k in EN)) EN[k] = el.getAttribute("alt");
    el.setAttribute("alt", t(k));
  });
  document.querySelectorAll("[data-i18n-aria]").forEach(function (el) {
    var k = el.getAttribute("data-i18n-aria");
    if (!(k in EN)) EN[k] = el.getAttribute("aria-label");
    el.setAttribute("aria-label", t(k));
  });
  var b = document.getElementById("lang");
  if (b) { b.textContent = next === "fr" ? "EN" : "FR"; b.setAttribute("aria-label", next === "fr" ? "English" : "Français"); }
}

EN.copy = "Copy"; EN.copied = "Copied";
EN.tag_ok = "Works"; EN.tag_no = "Home refused"; EN.tag_block = "Home blocked by the TV";
EN.rep_none = "No TV shared yet.";
EN.rep_err = "The list could not load. It is also <a href=\"https://github.com/pimado1/ownyourtv/issues?q=label%3Atv-report\">on GitHub</a>.";

(function () {
  var saved = null;
  try { saved = localStorage.getItem("lang"); } catch (e) {}
  apply(saved || ((navigator.language || "").toLowerCase().indexOf("fr") === 0 ? "fr" : "en"));
  var b = document.getElementById("lang");
  if (b) b.addEventListener("click", function () {
    var next = lang === "fr" ? "en" : "fr";
    try { localStorage.setItem("lang", next); } catch (e) {}
    apply(next);
  });
})();
