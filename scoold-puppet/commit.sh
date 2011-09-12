# !/bin/bash

LBNAME="ScooldLB"
REGION="eu-west-1"
MODULESDIR=/usr/share/puppet/modules
MODNAME="scoold"
NODETYPE="unknown"

if [ -n "$1" ] && [ -n "$2" ]; then
	GROUP=$2			
	if [ "$GROUP" = "cassandra" ]; then
		NODETYPE="db"
	elif [ "$GROUP" = "glassfish" ]; then
		NODETYPE="web"
	elif [ "$GROUP" = "elasticsearch" ]; then
		NODETYPE="search"
	fi
	
	FILE1="$NODETYPE-instances.txt"
	FILE2="$NODETYPE-hostnames.txt"
	
	if [ "$1" = "init" ]; then				
		### commit to all hosts in group, get info about all web servers			
		ec2-describe-instances --region $REGION --filter group-name=$GROUP --filter "instance-state-name=running" | egrep ^INSTANCE | awk '{ print $2,$4,$15}' > $FILE1
		cat $FILE1 | awk '{print $2}' > $FILE2 # hostnames only
		echo "done."	
	elif [ "$1" = "all" ]; then		
		dbseeds=$(sed -n 1,2p db-instances.txt | awk '{ print $3" " }' | tr -d "\n" | awk '{ print $1","$2 }' | sed 's/,$//g')
		dbhosts=$(cat db-instances.txt | awk '{ print $3"," }' | tr -d "\n" | sed 's/,$//g')
		# set seed nodes to be the first two IPs
		sed -e "1,/\\\$dbseeds/ s/\\\$dbseeds.*/\\\$dbseeds = \"$dbseeds\"/" -i.bak ./$M ODNAME/manifests/init.pp
		# set hosts system param in domain.xml to be picked up by the web app
		sed -e "1,/\\\$dbhosts/ s/\\\$dbhosts.*/\\\$dbhosts = \"$dbhosts\"/" -i.bak ./$MODNAME/manifests/init.pp
		
		### special case for elasticsearch - download sqs river plugin first
		if [ "$GROUP" = "elasticsearch" ] && [ -e "jenkins.txt" ]; then
			AUTH="-u $(cat jenkins.txt)"
			ZIP="https://erudika.ci.cloudbees.com/job/scoold/ws/scoold-search/target/river-amazonsqs.zip"
			FILENAME=$(expr $ZIP : '.*/\(.*\)$')
			search1host=$(head -n 1 $FILE2)
			ssh -n ubuntu@$search1host "curl -s $AUTH $ZIP > ~/$FILENAME"
		fi	
		
		count=1	
		while read i; do
			instid=$(echo $i | awk '{ print $1 }')
			host=$(echo $i | awk '{ print $2 }')
			ipaddr=$(echo $i | awk '{ print $3 }')
			
	 		if [ -n "$host" ]; then		
				### set node id and set tag
				sed -e "1,/\\\$nodename/ s/\\\$nodename.*/\\\$nodename = \"$NODETYPE$count\"/" -i.bak ./$MODNAME/manifests/init.pp
				
				# skip autotagging and LB regging when running on local virtual machine
				if [ -z "$3" ]; then				
					ec2-create-tags --region $REGION $instid --tag Name="$NODETYPE$count"
		
					if [ "$NODETYPE" = "web" ]; then
						# register instance with LB
						$AWS_ELB_HOME/bin/elb-register-instances-with-lb $LBNAME --region $REGION --quiet --instances $instid
					fi
				fi
								
				### push updated puppet script
				echo "copying $MODNAME.zip to $NODETYPE$count..."
				zip -rq $MODNAME $MODNAME/
				scp $MODNAME.zip ubuntu@$host:~/				
				
	 			count=$((count+1))
	 		fi		
	 	done < $FILE1	
		
		### cleanup
		rm ./$MODNAME/manifests/*.bak
		
		echo "done. executing puppet code on each node..."
		### unzip & execute remotely
		pssh/bin/pssh -h $FILE2 -l ubuntu -t 0 -i "sudo rm -rf $MODULESDIR/$MODNAME; sudo unzip -qq ~/$MODNAME.zip -d $MODULESDIR/; sudo puppet apply -e 'include $MODNAME'"
	fi
elif [ "$1" = "munin" ]; then
	# clear old hosts
	MCONF="./$MODNAME/files/munin.conf"
	db1host=""
	sed -e "/#begin/,/#end/d" -i.bak $MCONF
	### add the hostnames of all munin nodes to the munin server config
	echo "#begin" >> $MCONF
	for grp in "db" "web" "search"; do			
		count=1
		while read line; do				
			if [ -n "$line" ]; then
				ipaddr=$(echo $line | awk '{ print $3 }')

				if [ "$grp" = "db" ] && [ $count = 1 ]; then					
					db1host=$(echo $line | awk '{ print $2 }')
				else
					echo "[$grp$count.scoold.com]" 	>> $MCONF
					echo "    address $ipaddr" 		>> $MCONF
					echo "    use_node_name yes" 	>> $MCONF
					echo ""							>> $MCONF
				fi
				count=$((count+1))
			fi
		done < "$grp-instances.txt"		
	done
	echo "#end" >> $MCONF
	
	### copy new munin.conf to db1
	scp $MCONF ubuntu@$db1host:~/
	ssh -n ubuntu@$db1host "sudo cp ~/munin.conf /etc/munin/"
	
	### cleanup
	rm ./$MODNAME/files/*.bak
elif [ "$1" = "initdb" ]; then
	### load schema definition from file on db1
	ssh -n ubuntu@$db1host "sudo -u cassandra /home/cassandra/cassandra/bin/cassandra-cli -h localhost -f /usr/share/puppet/modules/scoold/files/schema.txt"
fi
