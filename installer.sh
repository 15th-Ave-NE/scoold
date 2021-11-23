#!/bin/bash
set -e -x

# Lightsail/DigitalOcean installer script for Ubuntu
VERSION="1.46.5"
PORT="8000"
WORKDIR="/home/ubuntu"
JARURL="https://github.com/15th-Ave-NE/scoold/blob/master/target/scoold-${VERSION}.jar"
sfile="/etc/systemd/system/scoold.service"

apt-get update && apt-get install -y wget openjdk-11-jre &&
wget -O scoold.jar ${JARURL} && \
mv scoold.jar $WORKDIR && \
chown ubuntu:ubuntu ${WORKDIR}/scoold.jar && \
chmod +x ${WORKDIR}/scoold.jar
touch ${WORKDIR}/application.conf && \
chown ubuntu:ubuntu ${WORKDIR}/application.conf

# Feel free to paste your Scoold configuration here
cat << EOF > ${WORKDIR}/application.conf
# UI config
para.app_name="You Only Live Once"
para.show_branding = false
para.footer_links_enabled = false
para.dark_mode_enabled = true
para.small_logo_url = "https://yolo-email-img.s3.amazonaws.com/logo.png"
para.logo_width = 110
para.favicon_url = ""

# Mail Config
# system email address
para.support_email = "admin@15thavene.org"
para.mail.host = "email-smtp.us-east-1.amazonaws.com"
para.mail.port = 465
para.mail.username = "AKIAZGL65KFVNBLLULZC"
para.mail.password = "BFbnQGt+TJGmy0WI7MHQ4r1k/jJeuCtMWXTxInLcwQp0"
para.mail.tls = true
para.mail.ssl = true
# enable SMTP debug logging
para.mail.debug = false


# App Config
para.access_key = "app:yuanxi"
para.secret_key = "xzRdTpCXrIeo49QUurkakPOQRWbytM4JeqGjp3IB5UBTJXIGEQQCbg=="
para.endpoint = "https://paraio.com"
# add your email here
para.admins = "liyuanxi23@gmail.com"
# (optional) require login to view content
para.is_default_space_public = false
EOF

touch $sfile
cat << EOF > $sfile
[Unit]
Description=YOLO
After=syslog.target
[Service]
WorkingDirectory=${WORKDIR}
SyslogIdentifier=YOLO
ExecStart=java -jar -Dconfig.file=application.conf scoold.jar
User=ubuntu
[Install]
WantedBy=multi-user.target
EOF

# This is optional. These rules might interfere with other web server configurations like nginx and certbot.
#iptables -t nat -A PREROUTING -p tcp -m tcp --dport 80 -j REDIRECT --to-port ${PORT} && \
#iptables -t nat -A OUTPUT -p tcp --dport 80 -o lo -j REDIRECT --to-port ${PORT}

systemctl enable scoold.service && \
systemctl start scoold.service
