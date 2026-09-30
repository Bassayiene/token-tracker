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

## 4. Envoyer le code

Depuis ton PC, dans `D:\projet TN` (Git Bash). On exclut les dépendances et builds, reconstruits sur le VPS :

```bash
tar --exclude=node_modules --exclude=dist --exclude=.angular --exclude=target --exclude=.env --exclude=.git \
    -czf token-tracker.tgz backend frontend deploy docker-compose.prod.yml .env.example
scp token-tracker.tgz user@IP_DU_VPS:~
```

Sur le VPS :

```bash
mkdir -p ~/token-tracker && tar -xzf ~/token-tracker.tgz -C ~/token-tracker && cd ~/token-tracker
```

(Plus tard, avec un dépôt Git distant, `git clone` / `git pull` remplacera cette étape.)

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
| Mettre à jour après un nouvel envoi du code | `dc up -d --build` |
| Logs | `dc logs -f --tail=200 backend` |
| Redémarrer | `dc restart backend` |
| Arrêter (données conservées) | `dc down` |

**Sauvegarde de la base** (à planifier, par exemple chaque nuit avec `crontab -e`) :

```bash
docker compose -f ~/token-tracker/docker-compose.prod.yml exec -T postgres \
  pg_dump -U token_tracker token_tracker | gzip > ~/backup-token-tracker-$(date +%F).sql.gz
```

**Restauration :**

```bash
gunzip -c backup.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U token_tracker token_tracker
```

## Points d'attention

- **Ne jamais utiliser `docker compose down -v`** en production : cela supprime la base et les certificats.
- **Le mot de passe admin n'est lu qu'à la création du compte.** Changer `ADMIN_PASSWORD` ensuite ne modifie rien.
- **Changer `JWT_SECRET` déconnecte tout le monde.** C'est sans autre conséquence.
- **Petit VPS (1 Go de RAM) :** le build Maven/Angular peut échouer faute de mémoire.
  Ajoute un swap de 2 Go :
  `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`.
  Autre solution : construire les images sur ton PC et les pousser sur un registre.
- **Heure :** toutes les dates sont en UTC. La synchronisation horaire tourne à hh:05, la quotidienne à 01:15 UTC.
