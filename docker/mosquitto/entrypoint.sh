#!/bin/sh
# Builds the Mosquitto password file from environment variables, so no credential is committed,
# then hands over to the image's own entrypoint.
set -eu

: "${MQTT_USERNAME:?MQTT_USERNAME must be set}"
: "${MQTT_PASSWORD:?MQTT_PASSWORD must be set}"

mkdir -p /mosquitto/auth
mosquitto_passwd -b -c /mosquitto/auth/passwd "$MQTT_USERNAME" "$MQTT_PASSWORD"
# Mosquitto refuses (or warns about) password files readable by other users.
chown -R mosquitto:mosquitto /mosquitto/auth
chmod 0700 /mosquitto/auth
chmod 0600 /mosquitto/auth/passwd

exec /docker-entrypoint.sh /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf
