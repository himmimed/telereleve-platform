# Guide utilisateur - Plateforme de télérelève CEAC

Ce guide s'adresse aux personnes qui utilisent la plateforme au quotidien : opérateurs de supervision,
techniciens d'intervention et administrateurs. Il ne demande aucune connaissance technique.

> Toutes les données de la plateforme de démonstration sont simulées : les compteurs, les clients et les
> adresses sont fictifs.

*[Capture d'écran : tableau de bord]*

## Sommaire

1. [Connexion et rôles](#1-connexion-et-rôles)
2. [Tableau de bord](#2-tableau-de-bord)
3. [Carte des compteurs](#3-carte-des-compteurs)
4. [Compteurs](#4-compteurs)
5. [Alertes](#5-alertes)
6. [Administration](#6-administration)
7. [Questions fréquentes](#7-questions-fréquentes)

## 1. Connexion et rôles

Ouvrez l'adresse de la plateforme (en démonstration : <http://localhost:4200>), saisissez votre
identifiant et votre mot de passe, puis cliquez sur **Se connecter**.

Chaque compte a un rôle qui détermine ce qu'il peut voir et faire :

| Fonction | Administrateur | Opérateur | Technicien |
|---|:-:|:-:|:-:|
| Tableau de bord et carte | oui | oui | oui |
| Liste et détail des compteurs, export CSV | oui | oui | non |
| Créer ou modifier un compteur | oui | non | non |
| Voir les alertes | toutes | toutes | les siennes |
| Prendre en charge, résoudre une alerte | non | oui | les siennes |
| Rouvrir une alerte, l'assigner à un technicien | non | oui | non |
| Gérer les utilisateurs et les seuils de détection | oui | non | non |

Le menu de gauche n'affiche que les pages auxquelles votre rôle donne accès.

**Bon à savoir**
- Pour votre sécurité, la session n'est pas conservée par le navigateur : **recharger la page (F5)
  vous déconnecte**. Naviguez avec le menu de l'application.
- La session expire après 8 heures ; reconnectez-vous simplement.
- Le bouton en bas du menu bascule entre le thème clair et le thème sombre.
- Les heures affichées sont toujours celles du Maroc (UTC+1), quel que soit le réglage de votre ordinateur.

## 2. Tableau de bord

C'est la page d'accueil. Elle se met à jour toute seule toutes les 30 secondes ; le bouton
**Actualiser** force une mise à jour immédiate.

- **Indicateurs** en haut de page :
  - compteurs actifs ;
  - compteurs silencieux, c'est-à-dire sans relève depuis plus de 3 fois leur intervalle ;
  - alertes ouvertes, par sévérité ;
  - consommation du jour depuis minuit (électricité en kWh, eau en m³).
- **Consommation du réseau** : courbe de tous les compteurs, par heure sur 24 heures ou par jour sur
  7 jours. Les boutons **Électricité** et **Eau** changent l'énergie affichée (les kWh et les m³ ne
  s'additionnent pas). Le tableau sous la courbe donne les valeurs exactes.
- **Alertes à traiter** : les alertes nouvelles et en cours les plus graves. **Tout voir** ouvre la page
  des alertes.
- **État du parc** : nombre de compteurs normaux, en alerte et silencieux. **Ouvrir la carte** les montre
  sur la carte.

## 3. Carte des compteurs

La carte montre les compteurs actifs autour de Fès. La couleur et la forme du marqueur indiquent l'état :

| État | Signification |
|---|---|
| Normal | Le compteur transmet et n'a aucune alerte ouverte |
| En alerte | Au moins une alerte est ouverte sur ce compteur |
| Silencieux | Plus de relève depuis plus de 3 intervalles : panne, coupure réseau ou fraude possible |

Les boutons au-dessus de la carte filtrent par état et indiquent le nombre de compteurs de chacun. Un clic
sur un marqueur affiche le numéro de série, le client et l'état ; les opérateurs et administrateurs peuvent
alors ouvrir la fiche du compteur, les techniciens leurs alertes (**Voir mes alertes**).

*[Capture d'écran : carte]*

## 4. Compteurs

*(Administrateur et opérateur)*

### Liste

La liste est triée par numéro de série. Utilisez le champ **Rechercher** (numéro de série, client ou
adresse) et les filtres **Type** et **Statut**. Cliquez sur une ligne pour ouvrir la fiche du compteur.

L'administrateur crée un compteur avec **Nouveau compteur**. Le numéro de série suit le format
`CEAC-E-000123` pour l'électricité et `CEAC-W-000123` pour l'eau. La lettre doit correspondre au type.

### Fiche d'un compteur

- **Informations** : client, adresse, position sur une petite carte, intervalle de relève, date de mise en
  service, statut.
- **Dernière relève** : index, tension (électricité, avec la plage autorisée), qualité du signal radio.
- **Consommation** : sur 24 heures (par heure), 7 ou 30 jours (par jour) ou 12 mois (par mois). Une
  période sans relève apparaît à 0 ; la consommation n'est jamais négative.
- **Historique des alertes** du compteur.
- **Exporter CSV** télécharge les relevés de la période affichée (24 h, 7 jours, 30 jours ou 12 mois)
  dans un fichier qui s'ouvre directement dans Excel (séparateur point-virgule, virgule décimale).
- L'administrateur peut **modifier le compteur**, par exemple le passer en statut *Inactif* lors d'une
  dépose. Un compteur inactif n'est plus surveillé.

*[Capture d'écran : fiche d'un compteur]*

## 5. Alertes

La plateforme analyse chaque relève et lève une alerte quand elle détecte une anomalie :

| Règle | Sévérité | Ce qu'elle signale | Que faire |
|---|---|---|---|
| A1 Consommation nulle | Moyenne | L'index ne bouge plus depuis 24 h | Vérifier l'occupation du local, un compteur bloqué ou une fraude |
| A2 Pic de consommation | Moyenne | La dernière heure dépasse 3 fois la consommation habituelle de cette heure | Fuite d'eau, appareil défectueux, usage anormal |
| A3 Compteur silencieux | Haute | Plus aucune relève depuis plus de 3 intervalles | Panne du compteur ou du réseau de communication |
| A4 Index décroissant | Critique | L'index a baissé | Fraude possible ou compteur remplacé : vérification sur place |
| A5 Tension hors plage | Moyenne | Tension hors de 230 V ± 10 % | Problème sur le réseau électrique |

- Les alertes **hautes et critiques** sont aussi envoyées par e-mail.
- Une même anomalie ne crée qu'**une seule** alerte tant qu'elle n'est pas résolue.
- Quand l'anomalie disparaît, l'alerte se **résout automatiquement**. Seule l'alerte A4 (index
  décroissant) exige une vérification sur place et une résolution manuelle.

### Traiter une alerte

La page **Alertes** liste les alertes, les plus récentes d'abord. Filtrez avec **Statut**, **Sévérité** et
**Règle** ; **Réinitialiser les filtres** revient à la liste complète. Le bouton d'actions en fin de ligne
propose, selon votre rôle et l'état de l'alerte :

| Action | Effet | Qui |
|---|---|---|
| **Prendre en charge** | L'alerte passe de *Nouvelle* à *En cours* | Opérateur, technicien assigné |
| **Marquer comme résolue** | L'alerte passe à *Résolue* | Opérateur, technicien assigné |
| **Rouvrir** | Une alerte résolue redevient *Nouvelle* | Opérateur |
| **Assigner à** | Confie l'alerte à un technicien | Opérateur |
| **Retirer l'assignation** | L'alerte n'est plus assignée | Opérateur |

**Circuit habituel**

1. L'opérateur voit une nouvelle alerte et l'assigne à un technicien.
2. Le technicien la retrouve dans sa page **Alertes**, qui ne montre que ses propres alertes, puis la
   prend en charge.
3. Après l'intervention, le technicien la marque comme résolue.

*[Capture d'écran : liste des alertes]*

## 6. Administration

*(Administrateur)*

### Utilisateurs

- **Nouvel utilisateur** : identifiant (lettres minuscules, chiffres, point ou tiret), nom, e-mail, rôle
  et mot de passe (8 caractères au moins).
- **Modifier l'utilisateur** : changer le rôle, désactiver le compte ou définir un nouveau mot de passe
  (laisser vide pour le conserver). Un compte désactivé perd l'accès immédiatement, même s'il était connecté.
- **Supprimer** : les alertes assignées à cette personne redeviennent non assignées.
- Pour éviter de bloquer la plateforme, un administrateur ne peut ni se supprimer, ni se désactiver, ni
  retirer son propre rôle, et le dernier administrateur actif ne peut pas être supprimé.

### Règles d'alerte

Chaque règle peut être **activée** ou désactivée, et ses seuils ajustés, puis **Enregistrer** :

| Règle | Réglages (valeur par défaut) |
|---|---|
| A1 | Fenêtre sans consommation (24 h) |
| A2 | Multiplicateur (× 3) et historique de référence (168 h, 72 h au minimum) |
| A3 | Multiple de l'intervalle de relève (× 3) |
| A4 | Aucun seuil |
| A5 | Tolérance autour de 230 V (10 %) |

Les nouveaux réglages s'appliquent dès la relève suivante.

## 7. Questions fréquentes

**J'ai été déconnecté en rechargeant la page.** C'est normal : la session n'est pas enregistrée dans le
navigateur, pour qu'un programme malveillant ne puisse pas la lire. Reconnectez-vous.

**« Vous n'avez pas les droits nécessaires pour cette action. »** Votre rôle ne permet pas cette
action ; demandez à un administrateur.

**Un compteur est silencieux mais n'a pas encore d'alerte A3.** Le contrôle des compteurs silencieux a lieu
toutes les 5 minutes ; l'alerte apparaît au plus tard au contrôle suivant.

**La consommation d'une heure est à 0.** Aucune relève n'est arrivée pendant cette heure. La consommation
de la période manquante est comptée dans l'heure de la relève suivante.

**Je ne reçois pas les e-mails d'alerte.** Seules les alertes hautes et critiques sont envoyées. En
démonstration, les e-mails sont visibles sur <http://localhost:8025> (Mailpit).
