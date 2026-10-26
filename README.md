# ParadoxXx 4K Tester

Appli Android TV qui dit, en langage simple, ce qu'un boîtier sait lire
(Full HD, 4K, HDR10, Dolby Vision, 8K, AV1) et si sa connexion suit.

## Déroulement (~3 min)
1. **Test de débit** : ~7 s de téléchargement depuis repo.jellyfin.org.
2. **13 vidéos** lues 10 s chacune en streaming (ExoPlayer / Media3).
   Avant chaque vidéo, l'appli interroge `MediaCodecList` : si le boîtier
   déclare ne pas savoir décoder le format ou la résolution, le test est
   marqué ❌ immédiatement, sans rien télécharger.
3. **Résultats** : résumé grand public + bouton « Détails techniques »
   (décodeur utilisé, matériel/logiciel, images perdues, coupures).

Une saccade avec un débit suffisant = boîtier trop faible.
Une saccade avec un débit insuffisant = connexion trop lente (📶).

## Compiler avec GitHub Actions
1. Crée un dépôt GitHub et pousse ce dossier tel quel.
2. Onglet **Actions** → workflow **Build APK** (se lance à chaque push,
   ou manuellement via *Run workflow*).
3. À la fin, télécharge l'artefact **ParadoxXx-4K-Tester** (zip contenant l'APK).

## Signature
Si les secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`
sont définis (Settings › Secrets and variables › Actions), l'APK est signé avec cette
clé fixe : les mises à jour s'installent par-dessus. Sinon, clé debug temporaire
(désinstaller avant chaque mise à jour).
Garder une copie du fichier .jks : sans lui, plus de mise à jour possible.

## Crédits
Vidéos de test : Jellyfin / Gnattu — licence Creative Commons BY-SA.
