# ZKTeco Fingerprint Manager

Application Java moderne pour la gestion et l'analyse d'empreintes digitales utilisant le SDK ZKFinger de ZKTeco.

## 📋 Description

Cette application permet de :
- Capturer des empreintes digitales via un scanner ZKTeco
- Enrôler des utilisateurs (enregistrement d'empreintes)
- Identifier et vérifier des empreintes (1:N et 1:1)
- Exporter des empreintes en images (JPEG, PNG, BMP)
- **Gérer plusieurs doigts simultanément** avec export en lot

## 🔧 Prérequis

Avant de lancer l'application, assurez-vous d'avoir :

- **Java JDK** (version 7 ou supérieure) installé et configuré dans le PATH
- **SDK ZKFinger SDK 5.x** ou **ZKOnline SDK 5.x** installé sur le système
- **Scanner d'empreintes ZKTeco** connecté via USB
- **Bibliothèques Java** :
  - `ZKFingerReader.jar` (dans le dossier `lib/`)
  - `flatlaf-3.5.2.jar` (pour l'interface moderne)

## 📁 Structure du projet

```
ZKFinger Demo2/
├── src/
│   └── com/
│       └── zkteco/
│           └── biometric/
│               ├── ModernZKFingerDemo.java  # Application principale
│               └── ZKFPDemo.java            # Ancienne version (démo)
├── lib/
│   ├── ZKFingerReader.jar                   # SDK ZKFinger
│   └── flatlaf-3.5.2.jar                    # Thème UI moderne
├── bin/                                      # Classes compilées (généré)
├── run_modern.bat                            # ⭐ Script de lancement principal
└── launch.bat                                # Script alternatif (ancienne version)
```

## 🚀 Lancement du projet

### Méthode recommandée : `run_modern.bat`

Le script **`run_modern.bat`** est la méthode la plus simple et la plus fiable pour lancer l'application. Il gère automatiquement la compilation et l'exécution.

#### Comment utiliser `run_modern.bat`

1. **Ouvrez un terminal** (Invite de commandes ou PowerShell) dans le répertoire du projet
   
   OU

2. **Double-cliquez** directement sur le fichier `run_modern.bat`

#### Que fait le script ?

Le script `run_modern.bat` effectue automatiquement les étapes suivantes :

1. **Vérifications préliminaires** :
   - ✅ Vérifie que Java est installé et accessible
   - ✅ Vérifie la présence de `ZKFingerReader.jar` dans `lib/`
   - ✅ Crée le dossier `bin/` s'il n'existe pas

2. **Compilation** :
   - Compile `ModernZKFingerDemo.java` avec encodage UTF-8
   - Inclut automatiquement les bibliothèques nécessaires dans le classpath
   - Place les fichiers `.class` compilés dans `bin/`

3. **Lancement** :
   - Exécute l'application avec le classpath correct
   - Le terminal reste ouvert pour afficher les messages et erreurs éventuelles

#### Détails techniques du script

```batch
# Vérification Java
where java >nul 2>&1

# Vérification bibliothèque
if not exist "%LIB_DIR%\ZKFingerReader.jar"

# Compilation avec encodage UTF-8
javac -d "%BIN_DIR%" -cp %CLASSPATH_COMPILE% -encoding UTF-8 "%MAIN_SRC%"

# Lancement
java -cp %CLASSPATH_RUN% %MAIN_CLASS%
```

#### Paramètres importants

- **Encodage UTF-8** : Le script utilise `-encoding UTF-8` pour supporter correctement les caractères spéciaux et emojis dans l'interface
- **Classpath automatique** : Les bibliothèques dans `lib/` sont automatiquement incluses
- **Gestion d'erreurs** : Le script s'arrête et affiche un message en cas d'erreur

### Alternative : Compilation manuelle

Si vous préférez compiler manuellement :

```bash
# 1. Compilation
javac -d bin -cp "lib/*" -encoding UTF-8 src/com/zkteco/biometric/ModernZKFingerDemo.java

# 2. Lancement
java -cp "bin;lib/*" com.zkteco.biometric.ModernZKFingerDemo
```

## 💡 Fonctionnalités principales

### 1. Connexion à l'appareil

- Cliquez sur **"Connecter Appareil"** dans le panneau de gauche
- L'application détecte automatiquement le scanner connecté
- L'aperçu de l'empreinte s'affiche en temps réel au centre

### 2. Enrôlement (Enregistrement)

- Cliquez sur **"Nouvel Enrôlement"**
- Posez le doigt **3 fois** sur le scanner
- L'application fusionne les 3 captures pour créer un template robuste
- Un ID unique est attribué automatiquement

### 3. Identification et Vérification

- **Identification (1:N)** : Cliquez sur **"Vérifier (1:1)"** pour activer le mode identification continue
  - Compare l'empreinte scannée avec toutes celles enregistrées
  - Affiche l'ID correspondant et le score de correspondance

- **Vérification (1:1)** : Mode passif après enrôlement
  - Compare avec la dernière empreinte enregistrée

### 4. Export d'images

- **Export simple** : Cliquez sur **"Exporter en JPEG/PNG"** pour sauvegarder l'empreinte actuellement affichée
- **Export multi-doigts** : Voir section ci-dessous

### 5. Enregistrement Multi-Doigts ⭐ (Nouveau)

Fonctionnalité avancée pour capturer et exporter plusieurs doigts en une seule opération :

1. **Sélection des doigts** :
   - Cochez les doigts que vous souhaitez scanner dans le panneau de droite
   - Les doigts sont organisés par main (Droite/Gauche)

2. **Capture** :
   - Sélectionnez un doigt dans la liste déroulante (seuls les doigts cochés apparaissent)
   - Cliquez sur **"📸 Capturer ce doigt"**
   - Posez le doigt sur le scanner
   - Répétez pour chaque doigt souhaité

3. **Export en lot** :
   - Cliquez sur **"💾 Exporter tous les doigts"**
   - Choisissez le dossier de destination
   - Toutes les empreintes sont exportées automatiquement avec des noms uniques :
     - **Dossier** : `scanfinger-YYYYMMDD-HHMM-XXXXXXXX/`
     - **Fichiers** : `scanfinger-YYYYMMDD-HHMM-XXXXXXXX-queldoigt.jpg`
   
   Exemple :
   ```
   scanfinger-20241215-1430-a3f2b1c4/
   ├── scanfinger-20241215-1430-a3f2b1c4-pouce_droite.jpg
   ├── scanfinger-20241215-1430-a3f2b1c4-index_droite.jpg
   └── scanfinger-20241215-1430-a3f2b1c4-pouce_gauche.jpg
   ```

## 🔍 Dépannage (Troubleshooting)

### Erreur : "Java n'est pas dans le PATH"

**Solution** : Installez Java JDK et ajoutez-le au PATH système, ou utilisez une variable d'environnement `JAVA_HOME`.

### Erreur : "ZKFingerReader.jar manquant"

**Solution** : Vérifiez que le fichier `lib/ZKFingerReader.jar` existe. Il devrait être fourni avec le SDK ZKFinger.

### Erreur : "Aucun appareil détecté"

**Solutions** :
- Vérifiez que le scanner est bien branché en USB
- Installez les pilotes du scanner ZKTeco
- Vérifiez que le SDK ZKFinger SDK 5.x est installé sur le système
- Essayez de débrancher et rebrancher le scanner

### Erreur de compilation : "unmappable character"

**Solution** : Le script `run_modern.bat` utilise déjà `-encoding UTF-8`. Si vous compilez manuellement, n'oubliez pas d'ajouter ce paramètre.

### L'application se lance mais le scanner ne répond pas

**Solutions** :
- Vérifiez que le SDK est correctement installé (les DLL natives doivent être dans le PATH système)
- Redémarrez l'application
- Vérifiez les permissions administrateur si nécessaire

### Problème d'affichage ou d'interface

**Solution** : L'application utilise FlatLaf pour l'interface moderne. Si `flatlaf-3.5.2.jar` est manquant, l'interface utilisera le thème système par défaut.

## 📝 Notes importantes

- La fenêtre s'adapte automatiquement à **90% de la taille de l'écran** pour un affichage optimal
- Les logs d'événements s'affichent en temps réel dans la zone de texte en bas
- L'application utilise un thread séparé pour la capture, ce qui permet une interface fluide
- Les empreintes sont stockées temporairement en mémoire pendant la session

## 🔗 Ressources

- **SDK ZKFinger** : Documentation fournie avec le SDK
- **ZKTeco** : https://www.zkteco.com
- **Support SDK** : sdksupport@zkteco.com

## 📄 Licence

Voir la documentation du SDK ZKFinger pour les conditions d'utilisation.

