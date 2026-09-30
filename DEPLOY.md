# Déploiement sur un VPS

Architecture : seul **Caddy** est exposé (ports 80/443, HTTPS Let's Encrypt automatique).

```
Internet ──443──▶ caddy ──▶ frontend (nginx) ──/api──▶ backend ──▶ postgres
```

La base, le backend et le frontend ne sont joignables que par le réseau Docker interne.
Swagger et `/management` (métriques) ne sont pas publiés.

**Prérequis :** Ubuntu/Debian, au moins **2 Go de RAM** (la compilation Maven + Angular se fait sur le VPS ;
voir « Petit VPS » plus bas sinon), un nom de domaine.

---

## 1. DNS

Chez ton registrar, crée un enregistrement **A** `ton-domaine` → IP du VPS (et **AAAA** si le VPS a une IPv6).
Vérifie depuis ton PC avant de continuer (Caddy ne peut pas obtenir le certificat tant que le DNS ne pointe pas) :

```bash
nslookup ton-domaine
```

## 2. Arrêter l'ancien projet

Sur le VPS, dans le dossier de l'ancien projet :

```bash
docker compose ps           # voir ce qui tourne
docker compose down         # arrête et supprime les conteneurs, GARDE les volumes (données)
```

> ⚠️ `docker compose down -v` supprimerait aussi ses données (bases, fichiers). Ne l'ajoute qu'une fois sûr
> de ne plus en avoir besoin, après une sauvegarde si nécessaire.

Vérifie que les ports 80 et 443 sont libres (aucune ligne ne doit s'afficher) :

```bash
sudo ss -tlnp | grep -E ':(80|443)\s'
```

Si un nginx/apache installé hors Docker les occupe : `sudo systemctl disable --now nginx` (ou `apache2`).

## 3. Docker et pare-feu

Si Docker n'est pas déjà installé :

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER    # puis se déconnecter / reconnecter
```

Pare-feu (garde bien SSH ouvert avant d'activer) :

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
```

## 4. Récupérer le code

Sur le VPS :

```bash
sudo apt-get install -y git     # si git n'est pas installé
git clone https://github.com/Bassayiene/token-tracker.git ~/token-tracker
cd ~/token-tracker
```

Si le dépôt est **privé**, GitHub demande un identifiant : utilise ton nom d'utilisateur et un
**Personal Access Token** en lecture seule (GitHub > Settings > Developer settings > Fine-grained tokens,
accès « Contents: Read-only » limité à ce dépôt), pas ton mot de passe. Autre solution : une *deploy key* SSH
(Settings du dépôt > Deploy keys), avec l'URL `git@github.com:Bassayiene/token-tracker.git`.

## 5. Configurer `.env`

```bash
cp .env.example .env
openssl rand -base64 24    # -> DB_PASSWORD
openssl rand -base64 32    # -> JWT_SECRET
nano .env
chmod 600 .env
```

À renseigner : `DOMAIN` (sans `https://`), `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`
(mot de passe solide : c'est le compte qui peut ajouter des tokens et lancer les synchronisations).

Le démarrage est refusé tant qu'une de ces valeurs manque.

## 6. Lancer

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Le premier build prend plusieurs minutes. Suivre le démarrage :

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend caddy
```

Attendu dans les logs : `Admin account admin created` (backend) et `certificate obtained successfully` (caddy).
Ouvre ensuite `https://ton-domaine` et connecte-toi avec le compte admin.

Au premier démarrage, le backend récupère l'historique des prix (30 jours) : les graphiques se remplissent
après une ou deux minutes.

---

## Exploitation

Toutes les commandes se lancent dans `~/token-tracker`. Alias pratique :
`alias dc='docker compose -f docker-compose.prod.yml'`.

| Action | Commande |
|---|---|
| Mettre à jour (après un push depuis ton PC) | `git pull && dc up -d --build` |
| Logs | `dc logs -f --tail=200 backend` |
| Redémarrer | `dc restart backend` |
| Arrêter (données conservées) | `dc down` |

## Sauvegardes

Le conteneur `backup` sauvegarde la base **chaque nuit à 01:45 UTC**, juste après la collecte quotidienne.
Chaque dump est vérifié avant d'être gardé.

Les fichiers vont dans `~/token-tracker/backups/` :

| Dossier | Contenu | Conservés |
|---|---|---|
| `daily/` | une sauvegarde par jour | les 14 dernières |
| `weekly/` | celle du dimanche | les 8 dernières |
| `monthly/` | celle du 1er du mois | les 12 dernières |

La rotation se fait par nombre de fichiers, pas par âge. Si les sauvegardes s'arrêtent, les anciennes ne sont donc
jamais supprimées. Le nombre de copies et l'horaire se règlent dans `.env` avec `BACKUP_KEEP_DAILY`,
`BACKUP_KEEP_WEEKLY`, `BACKUP_KEEP_MONTHLY` et `BACKUP_CRON`.

| Action | Commande |
|---|---|
| État (doit être `healthy`) | `dc ps backup` |
| Journal des sauvegardes | `dc logs --tail=50 backup` |
| Sauvegarder maintenant (avant une mise à jour risquée, par ex.) | `dc exec backup sh /scripts/backup.sh` |
| Lister les sauvegardes | `ls -lh backups/*/` |

Le conteneur passe **`unhealthy`** si aucune sauvegarde n'a réussi depuis 26 heures.

> ⚠️ Ces sauvegardes restent **sur le VPS**. Elles protègent contre une erreur (données supprimées, mauvaise mise
> à jour), **pas contre la perte du serveur**. Récupère-les régulièrement sur ton PC :
> `scp -r root@188.34.188.109:~/token-tracker/backups/monthly .` (ou `weekly`, `daily`).

### Restaurer une sauvegarde

La restauration **remplace toute la base** par le contenu du fichier choisi. Elle se fait en une seule transaction :
en cas d'erreur, rien n'est modifié.

```bash
cd ~/token-tracker
dc exec backup sh /scripts/backup.sh                    # sauvegarde de l'état actuel, par précaution
dc exec backup sh /scripts/restore.sh                   # liste les sauvegardes disponibles
dc stop backend
dc exec backup sh /scripts/restore.sh /backups/daily/<fichier>.dump --yes
dc start backend
```

Au redémarrage, le backend rattrape automatiquement les prix manquants depuis la date de la sauvegarde.

Sur un **nouveau serveur**, déploie d'abord le projet (étapes 1 à 6), copie le dossier `backups/`, puis suis la
même procédure.

## Points d'attention

- **Ne jamais utiliser `docker compose down -v`** en production : cela supprime la base et les certificats.
  Le dossier `backups/` survivrait, mais autant ne pas en avoir besoin.
- **Le mot de passe admin n'est lu qu'à la création du compte.** Changer `ADMIN_PASSWORD` ensuite ne modifie rien.
- **Changer `JWT_SECRET` déconnecte tout le monde.** C'est sans autre conséquence.
- **Petit VPS (1 Go de RAM) :** le build Maven/Angular peut échouer faute de mémoire.
  Ajoute un swap de 2 Go :
  `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`.
  Autre solution : construire les images sur ton PC et les pousser sur un registre.
- **Heure :** toutes les dates sont en UTC. La synchronisation horaire tourne à hh:05, la quotidienne à 01:15 UTC.
