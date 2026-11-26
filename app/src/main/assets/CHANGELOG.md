# Journal des modifications

## 1.14 - 2026-11-27

- Sécurité : le verrou freine les essais répétés (5 essais, puis une attente doublante jusqu'à
  15 minutes).
- Mode leurre : plus aucune notification de solde réel ne peut apparaître pendant une session leurre.
- Portefeuille : changer d'adresse, de source ou de clé d'affichage efface aussi le solde et les
  transactions de l'ancienne adresse.
- Cours : éditer ou importer un portefeuille ne relance plus la synchronisation complète.
- Graphiques : le cache garde 100 graphiques et n'oublie que le plus ancien.
- Recherche : le sélecteur de crypto trouve aussi par symbole (BTC, XMR…).
- Robustesse : cache des logos sûr en accès concurrent ; fichier chiffré corrompu signalé en français.

## 1.13 - 2026-11-23

- Mode leurre : les 4 portefeuilles utilisent désormais de vraies adresses publiques, avec leurs
  soldes réels lus sur la chaîne (plus aucune valeur inventée) ; les anciennes adresses factices sont
  remplacées automatiquement.
- Mode leurre : les portefeuilles factices se rafraîchissent et affichent leur historique comme les
  autres.
- Verrou : après la fermeture du processus par Android, le code est redemandé (avant, l'application
  pouvait se rouvrir déverrouillée).
- Portefeuille : changer la crypto efface le solde et les transactions de l'ancienne.
- Taux de change : une devise manquante est signalée au lieu d'être convertie à 1,0 ; la BCE complète
  les devises absentes de CoinGecko.
- Graphiques : une réponse arrivée en retard ne remplace plus la période affichée.
- Import : une adresse base58 (Solana, XRP, TRON) n'est plus confondue avec la même adresse écrite
  dans une autre casse.
- Fichier de portefeuilles illisible : l'application prévient et nomme la copie `.bad` conservée.
- Cours : la source Binance couvre aussi les cryptos du top 100.
- Un seul sélecteur de crypto pour l'accueil et l'ajout, avec recherche.
- Solana : le solde est de nouveau lu (le RPC public refusait l'agent « navigateur »).

## 1.12 - 2026-11-22

- Mode leurre : les marchés et les cours affichés sont les vrais ; seuls les portefeuilles sont
  factices.
- Mode leurre : les vrais portefeuilles ne peuvent plus apparaître une fraction de seconde après
  une fermeture forcée, et l'historique des portefeuilles factices n'est plus effacé en changeant
  de mode.
- Taux de change : repli sur les taux de la Banque centrale européenne (Frankfurter) quand
  CoinGecko ne répond pas ; un cours nul ne produit plus de taux infinis.
- Réglages : une préférence abîmée ne désactive plus le verrou, la sauvegarde précédente est
  restaurée.
- Soldes : un portefeuille dont la première synchro a échoué est réessayé, au lieu d'attendre 24 h.
- Import : déduplication immédiate, plus rapide sur les gros fichiers.

## 1.11 - 2026-11-22

- Confidentialité : l'aperçu de l'application dans les récents ne montre plus les portefeuilles
  avant l'écran de verrouillage.
- Verrou : l'application se verrouille dès la mise en pause (changement d'application), et plus
  seulement à l'arrêt complet.

## 1.10 - 2026-11-22

- Mode leurre : les marchés de l'accueil peuvent être ajoutés, déplacés et retirés, avec une liste
  propre au leurre ; la liste réelle n'est pas modifiée.
- Mode leurre : les clés API et le reste des réglages restent accessibles comme en mode normal ;
  seul le réglage du leurre est masqué.

## 1.9 - 2026-11-21

- Mode leurre : l'empreinte ou le visage ouvre uniquement les données factices ; le vrai code
  reste nécessaire pour les vraies données.
- Mode leurre : ajout, modification, suppression, import et export fonctionnent sur un jeu de
  données séparé (`decoy-wallets.json`) sans toucher aux vrais portefeuilles.
- Mode leurre : le code peut être modifié depuis les réglages, sans indiquer qu'il s'agit du leurre.
- Mode leurre : le changelog ne s'ouvre plus en touchant le numéro de version.

## 1.8 - 2026-11-20

- Sécurité : un code de leurre, saisi à l'écran de verrouillage à la place du vrai code, affiche
  4 portefeuilles (BTC, ETH, SOL, XRP) et 4 marchés factices. Les vrais portefeuilles restent cachés.
- Mode leurre : navigation normale sur les données factices, mais ajout, modification, suppression,
  import/export et sections sensibles des réglages (clés API, verrou) sont neutralisés ou masqués.
  L'empreinte ou le vrai code ouvrent les vraies données.
- Le code de leurre doit être différent du code de l'application et nécessite un PIN ou un schéma.

## 1.7 - 2026-11-20

- Verrou : l'empreinte/le visage est accepté (biométrie forte ou faible) ; le code du téléphone
  ne contourne plus le PIN ou le schéma de l'application.
- Portefeuilles : une entrée abîmée de `wallets.json` ne cache plus les autres portefeuilles.
- Hors ligne : l'interface et la synchro de fond fusionnent leurs écritures de `snapshot.json` au
  lieu de s'écraser.
- Cours : un seul appel réseau à la fois entre l'interface et le worker (moins d'erreurs 429).
- Monero : clé d'affichage validée en hexadécimal ; l'export lisible signale qu'elle peut être
  incluse.
- Graphiques : les périodes 2 ans / 5 ans / max passent par Binance pour les cryptos récentes.
- Synchro : la flèche d'actualisation tourne pendant le chargement ; le rond du pull-to-refresh
  n'apparaît que pendant le geste et disparaît au relâchement.
- Synchro : la barre d'avancement suit les portefeuilles terminés et la flèche continue de tourner,
  même quand les animations système sont désactivées.
- Divers : textes corrigés, code mort supprimé, User-Agent Chrome standard.
- Réglages : précision sur l'empreinte/le visage (proposé sur l'écran de verrouillage si une
  biométrie est enrôlée dans Android).
- Build : un seul APK par variante (sortie Gradle) ; plus de copie dans `dist/`.

## 1.6 - 2026-11-18

- Export : le code saisi avant le choix du fichier survit à une rotation ou à une fermeture de l'app.
- Liste des portefeuilles : un `wallets.json` illisible est mis de côté (`wallets.json.bad`) au lieu
  d'être écrasé par une liste vide.
- Hors ligne : les derniers cours connus sont conservés et le total reste juste, avec une icône et un
  message « Hors ligne — derniers cours connus du … ».
- Synchro de fond : seuls les cours des portefeuilles et de l'accueil sont téléchargés puis gardés ;
  appels limités à 10 s et 4 portefeuilles en parallèle.
- Graphiques : « 24 h » affiche bien 24 h (seul « 1 h » est limité aux 65 dernières minutes) ; le cache
  de graphiques est plafonné.
- Verrou : la vérification du code quitte le fil principal ; l'empreinte / le code du téléphone
  redeviennent disponibles en plus du PIN ou du schéma.
- QR code : confirmation et avertissement quand le portefeuille contient une clé d'affichage Monero.
- Import : la boîte de résultat détaille chaque portefeuille écarté (crypto inconnue, adresse
  invalide, doublon…).
- Scan QR : résultat renvoyé sur le fil principal et une image sur cinq analysée.
- Toutes les boîtes de dialogue : croix de fermeture à droite du titre, sans bouton « Fermer » (ni
  « Annuler ») en bas ; les actions principales (Supprimer, Exporter, Importer…) restent.
- Menus d'appui long (portefeuille, marché) : popup compact de 180 dp, titre 16 sp, actions serrées et
  grisées quand elles ne s'appliquent pas.
- Sauvegarde cloud Android désactivée : les adresses, la clé d'affichage et les clés API restent sur
  l'appareil.
- Nettoyage : dépendances inutilisées retirées, PBKDF2 factorisé, tests unitaires des formats.

## 1.5 — 2026-11-18

- Accueil : appui long sur un portefeuille pour le déplacer vers le haut ou le bas, ou le supprimer.
- Accueil : bouton + à côté de « Marchés » pour ajouter un graphique (liste filtrable) ; appui long sur
  un graphique pour le déplacer vers le haut ou le bas, ou le retirer. L'ordre des graphiques est
  mémorisé. Les menus d'appui long ont des icônes.
- Réglages : la section « Cryptos sur l'accueil » (doublon de l'accueil) est retirée.

## 1.4 — 2026-11-18

- Nouveau portefeuille : retour du scan d'un QR code, avec la caméra de l'appareil (CameraX + ZXing)
  au lieu du module Google qui échouait. Un QR de partage remplit la crypto, la source et l'adresse ;
  un QR contenant seulement une adresse remplit l'adresse de la crypto choisie.
- La permission caméra est demandée à la première utilisation.

## 1.3 — 2026-11-17

- Accueil : bloc « Solde total » plus compact, sous-titre inutile au-dessus des marchés retiré.
- Le scanner de QR code (indisponible sur les appareils sans module Google) est retiré du menu et de
  l'ajout ; l'export/import par fichier et le QR affiché sur la fiche restent disponibles.
- Ajout d'un portefeuille : liste des cryptos triée par ordre alphabétique, sources de données
  réparties sur plusieurs lignes (elles débordaient pour Ethereum).
- 14 nouvelles cryptos suivies : Polygon (POL), Arbitrum (ARB), Optimism (OP), NEAR, Algorand,
  Cosmos (ATOM), Hedera (HBAR), Stacks (STX), VeChain (VET), Aptos, Filecoin, Uniswap, Aave et Pepe.

## 1.2 — 2026-11-17

- Fiche portefeuille : message quand la source contient plus de transactions que celles affichées.
- Ethereum : sources etherscan.io et beaconcha.in (clé API gratuite dans Réglages › Clés API).
- Export protégé par un code optionnel (fichier chiffré AES-GCM), code demandé à l'import.
- Verrouillage par PIN ou schéma propre à l'application, à la place de l'empreinte et du code du téléphone.
- Liste des cryptos de l'accueil triée par ordre alphabétique.
- Réglages : « À propos » remonté en haut.
- APK de release signé : `CryptoWalletTracker-v1.2-release.apk`.

## 1.1 — 2026-11-16

- Icône dorée et nouvelle identité visuelle (fonds chauds, cartes ocre, mode sombre).
- Thèmes clair, sombre et automatique (système).
- 22 cryptos : les 20 premières capitalisations + Monero et Decred.
- Plusieurs portefeuilles par crypto, sans afficher les adresses sur l'accueil.
- Choix de la source de données par portefeuille, avec ce qui est disponible
  (solde seul ou solde + transactions) indiqué à l'ajout et modifiable ensuite.
- Devise d'affichage : euro, dollar, livre, yen, yuan, BTC ou ETH, en touchant le solde total.
- Deux sources de cours : CoinGecko et Binance.
- Graphiques interactifs (1 h, 24 h, 7 j, 30 j, 1 an, 2 ans, 5 ans, max) avec lecture
  du cours au doigt, axes gradués, source affichée et période mémorisée par crypto.
- Pull-to-refresh sur l'accueil, chaque portefeuille et chaque graphique.
- Date de dernière synchronisation affichée à côté de la source.
- Données conservées jusqu'à une synchro réussie ; alerte détaillant la donnée et la source en échec.
- Rafraîchissement automatique : 24 h pour cours, taux, soldes, historique et graphiques longs,
  1 h pour les graphiques courts, uniquement quand l'application est ouverte.
- Menu « Mettre à jour les logos » dans les trois points de l'accueil.
- Top 100 des cryptos pour les cours et les graphiques (22 avec lecture de solde).
- Verrouillage par empreinte ou code du téléphone, soldes masquables (icône œil).
- Synchro en arrière-plan (6/12/24 h) et notification en cas de changement de solde.
- Scan d'un QR code à l'ajout d'un portefeuille, icône QR et lien explorateur sur la fiche.
- Accueil : ajout via le + à côté du titre, solde total plus compact, indicateurs de défilement.
- Solde total partiel affiché avec une icône d'avertissement si un portefeuille manque.
- Correction du crash à l'export/import de fichier et à la demande de permission de notification.
- L'accueil n'affiche que les cryptos cochées dans les réglages.
- Page À propos.
- APK de release signé : `CryptoWalletTracker-v1.1-release.apk`.

## 1.0.0 — 2026-11-15

- Version initiale.
- Portefeuilles Ethereum, Decred et Monero : solde par portefeuille et total en euros.
- Nom et commentaire modifiables pour chaque portefeuille.
- Cours des trois cryptos avec graphique (1 jour, 7 jours, 30 jours, 1 an).
- Export / import des portefeuilles par fichier JSON et par QR code.
- Historique des transactions pour Ethereum et Decred.
- Numéro de version et changelog accessibles depuis l'accueil.
