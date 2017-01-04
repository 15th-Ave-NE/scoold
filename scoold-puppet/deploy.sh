# Copyright Erudika LLC
# https://erudika.com/
# 
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:
# 
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
# LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
# WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#
# deploy.sh - helper script for deloying/undeploying Scoold to/from Glassfish app server and 
# for executing utility tasks like updating CSS/JS/IMG assets

#!/bin/bash

# https://erudika.ci.cloudbees.com/job/scoold/ws/scoold-web/target/scoold-web/styles/style.css
  
F1SUFFIX="-instances.txt"
F2SUFFIX="-hostnames.txt"
FILE1="glassfish$F1SUFFIX"
FILE2="glassfish$F2SUFFIX"
KEYS="keys.txt"
JAUTH="jenkins-auth.txt"
ASADMIN="sudo -u glassfish /home/glassfish/glassfish/bin/asadmin"
LBNAME="ScooldLB"	
REGION="eu-west-1"
CONTEXT=""
ENABLED="false"
VERSION=$(date +%F-%H%M%S)
APPNAME="scoold-web-$VERSION"
WARFILE="scoold-web.war"
BUCKET="com.scoold.glassfish"
CSSDIR="../scoold-web/target/scoold-web/styles"
JSDIR="../scoold-web/target/scoold-web/scripts"
IMGDIR="../scoold-web/target/scoold-web/images"
JACSSIDIR="../scoold-web/target/scoold-web/jacssi"
HOMEDIR="/home/ubuntu/"
SUMSFILE="checksums.txt"
PATHSFILE="filepaths.txt"
JARPATH="../scoold-invalidator/target/scoold-invalidator-all.jar"
CIWARPATH="https://s3-eu-west-1.amazonaws.com/$BUCKET/"

function readProp () {
	FILE=$1
	echo $(sed '/^\#/d' $FILE | grep "$2" | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
}

AWSACCESSKEY=$(readProp $KEYS "com.scoold.awsaccesskey")
AWSSECRETKEY=$(readProp $KEYS "com.scoold.awssecretkey")
FBAPPID=$(readProp $KEYS "com.scoold.fbappid")
FBAPIKEY=$(readProp $KEYS "com.scoold.fbapikey")
FBSECRET=$(readProp $KEYS "com.scoold.fbsecret")
AWSCFDIST=$(readProp $KEYS "com.scoold.awscfdist")

function updateJacssi () {
	echo "Deploying javascript, css and images to CDN..."
	### copy javascript, css, images to S3				
	if [ -d $CSSDIR ] && [ -d $JSDIR ] && [ -d $IMGDIR ]; then
		dirlist="$CSSDIR/*min.css $CSSDIR/pictos* $JSDIR/*min.js $IMGDIR/*.gif $IMGDIR/*.png $IMGDIR/*.ico"
		rm -rf $SUMSFILE $PATHSFILE &> /dev/null
		if [ ! -d $JACSSIDIR ]; then
			mkdir $JACSSIDIR
		fi
		
		for file in `ls -d1 $dirlist`; do
			if [ -f $file ]; then
				name=$(expr "$file" : '.*/\(.*\)$')
				gzip -9 -c $file > $JACSSIDIR/$name
				echo "$name $(md5 -q $file)" >> $SUMSFILE
				echo "$name $JACSSIDIR/$name" >> $PATHSFILE				
			fi
		done
		
		if [ -n "$1" ] && [ "$1" = "invalidateall" ]; then
			SUMSFILE="all"
			PATHSFILE=""
		fi
	
		# upload to S3		
		java -Dawscfdist="$AWSCFDIST" -Dawsaccesskey="$AWSACCESSKEY" -Dawssecretkey="$AWSSECRETKEY" -jar $JARPATH $SUMSFILE $PATHSFILE
		rm -rf $JACSSIDIR
	fi
}

function setProperties () {
	dbhosts=$3
	eshosts=$4
	if [ -z "$3" ]; then
		dbhosts=$(ec2din --region $REGION --filter "instance-state-name=running" -F "tag-value=cassandra1" | egrep ^INSTANCE | awk '{ print $16}')
	fi	
	if [ -z "$4" ]; then
		eshosts=$(ec2din --region $REGION --filter "instance-state-name=running" -F "tag-value=elasticsearch1" | egrep ^INSTANCE | awk '{ print $16}')
	fi
	if [ -z "$eshosts" ]; then
		eshosts=$dbhosts
	fi
	
	
	prop1="com.scoold.workerid=$2"
	prop2="com.scoold.dbhosts=\"$dbhosts\""
	prop3="com.scoold.eshosts=\"$eshosts\""
	
	prop4="com.scoold.awsaccesskey=\"$AWSACCESSKEY\""
	prop5="com.scoold.awssecretkey=\"$AWSSECRETKEY\""
	#prop6="" #AWSSQSENDPOINT
	#prop7="" #AWSSQSQUEUEID
	prop8="com.scoold.fbappid=\"$FBAPPID\""
	prop9="com.scoold.fbsecret=\"$FBSECRET\""
	prop10="com.scoold.awscfdist=\"$AWSCFDIST\""	
	
	ssh -n ubuntu@$1 "$ASADMIN create-system-properties $prop1:$prop2:$prop3:$prop4:$prop5::$prop8:$prop9:$prop10"
}

function uploadWAR () {
	if [ -f $1 ] && [ "$1" != "s3" ]; then
		filesum=$(md5 -q $1)
		warurl="s3://$BUCKET/$filesum.war"
		# delete all files in bucket
		for war in $(s3cmd ls s3://$BUCKET | awk '{print $4}'); do
			s3cmd del $war
		done
		echo "Uploading $filesum.war to S3... $warurl"
		s3cmd -P put $1 $warurl
	fi
}

function deployWAR () {
	# params $1=HOST, $2=ENABLED, $3=OLDAPP
	if [ -n "$1" ] && [ -n "$2" ]; then
		host=$1
		oldapp="$3"
		prefix=""
		
		if [ `expr "$1" : '^glassfish[0-9]*$'` != 0 ]; then
			host=$(ec2din --region $REGION --filter "instance-state-name=running" -F "tag-value=$1" | egrep ^INSTANCE | awk '{ print $4}')
			nodeid=$(expr "$1" : '^glassfish\([0-9]*\)$')
			if [ -z "$3" ]; then
				oldapp=$(ssh -n ubuntu@$host "$ASADMIN list-applications --type web --long | grep enabled | awk '{ print \$1 }'")				
			fi			
		fi
		
		if [ "$2" = "true" ] && [ -n "$oldapp" ]; then
			prefix="$ASADMIN undeploy $oldapp;"
		fi
				
		setProperties $host $nodeid		
		
		warpath="$HOMEDIR$WARFILE"
		WARURL="$CIWARPATH$(expr `s3cmd ls s3://$BUCKET | awk '{print $4}'` : '.*/\(.*\)$')"
		ssh -n ubuntu@$host "wget -q -O $warpath $WARURL && chmod 755 $warpath && $prefix $ASADMIN deploy --force --enabled=$2 --name $APPNAME $warpath && rm $warpath"
	fi	
}

if [ "$1" = "updatejacssi" ]; then
	updateJacssi $2
elif [ "$1" = "setprops" ]; then	
	setProperties $2 $3 $4 $5
elif [ -n "$1" ] && [ -n "$2" ]; then	
	if [ "$1" = "cmd" ]; then
		if [ `expr "$2" : '^glassfish[0-9]*$'` != 0 ] && [ -n "$3" ]; then
			host=$(ec2din --region $REGION --filter "instance-state-name=running" -F "tag-value=$2" | egrep ^INSTANCE | awk '{ print $4}')
			if [ -n "$host" ]; then
				ssh -n ubuntu@$host "$ASADMIN $3"
			fi		
		else
			pssh/bin/pssh -h $FILE2 -l ubuntu -t 0 -i "$ASADMIN $2"		
		fi	
	elif [ `expr "$1" : '^glassfish[0-9]*$'` != 0 ]; then
		updateJacssi
		uploadWAR $2
		deployWAR $1 $3 $4
	else
		if [ "$2" !=  "true" ] && [ "$2" !=  "false" ]; then
			CONTEXT="--contextroot $2"
		fi

		if [ "$3" = "true" ] || [ "$2" = "true" ]; then
			ENABLED="true"
		fi	

		if [ -f $FILE1 ] && [ -f $FILE2 ]; then		
			echo "-------------------  BEGIN ROLLING UPGRADE  -----------------------"
			echo ""
			echo "Deploying '$APPNAME' [enabled=$ENABLED]"

			updateJacssi
			uploadWAR $1

			OLDAPP=""
			isok=false
			dbhosts=$(cat "cassandra$F1SUFFIX" | awk '{ print $3"," }' | tr -d "\n" | sed 's/,$//g')
			eshosts=$(cat "elasticsearch$F1SUFFIX" | awk '{ print $3"," }' | tr -d "\n" | sed 's/,$//g')			
			count=1		
			while read i; do
				if [ -n "$i" ]; then
					instid=$(echo $i | awk '{ print $1 }')
					host=$(echo $i | awk '{ print $2 }')			

					if [ -z "$OLDAPP" ]; then
						OLDAPP=$(ssh -n ubuntu@$host "$ASADMIN list-applications --type web --long | grep enabled | awk '{ print \$1 }'")
					fi
					
					### STEP 1: deploy .war file to all servers	(enabled if deployed for the first time, disabled otherwise)			
					deployWAR $host $ENABLED $OLDAPP
					
					if [ "$ENABLED" = "false" ] && [ -n "$host" ]; then							
						### STEP 2: deregister each instance from the LB, consecutively 
						$AWS_ELB_HOME/bin/elb-deregister-instances-from-lb $LBNAME --region $REGION --quiet --instances $instid
						sleep 10

						### STEP 3: disable old deployed application and enable new application
						ssh -n ubuntu@$host "$ASADMIN disable $OLDAPP && $ASADMIN enable $APPNAME"

						if [ $isok = false ]; then
							### STEP 4: TEST, TEST, TEST! then continue
							echo "TEST NOW! => $host"
							read -p "'ok' to confirm > " response < /dev/tty
						fi

						if [ "$response" = "ok" ] || [ $isok = true ]; then
							### STEP 5.1 undeploy old application
						    echo "OK! Undeploying old application..."
							ssh -n ubuntu@$host "$ASADMIN undeploy $OLDAPP"
							isok=true
						else
							### STEP 5.2 REVERT back to old application
							echo "NOT OK! Reverting back to old application..."
							ssh -n ubuntu@$host "$ASADMIN disable $APPNAME && $ASADMIN enable $OLDAPP"
							isok=false
						fi			
					fi					
					count=$((count+1))
					### STEP 6: register application back with the LB
					$AWS_ELB_HOME/bin/elb-register-instances-with-lb $LBNAME --region $REGION --quiet --instances $instid
					sleep 6
				fi
			done < $FILE1		
			echo ""
			echo "---------------------------- DONE ---------------------------------"	
		fi
	fi
else
	echo "USAGE:  $0 glassfishXXX warfile enabled [context] | updatejacssi [invalidateall] | cmd [ glassfishXXX ] gfcommand | setprops host workerid dbhosts eshosts"
fi

