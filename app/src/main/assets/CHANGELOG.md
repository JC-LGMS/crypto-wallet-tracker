# Journal des modifications

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
