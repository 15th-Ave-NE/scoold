class scoold::cassandra {

	$cassandrausr = "cassandra"
	$cassandrahome = "/home/cassandra"	
	$caspath = "${cassandrahome}/cassandra.tar.gz"
	$casdir = "${cassandrahome}/cassandra"
	$casconf = "${casdir}/conf/cassandra.yaml"	
	$dontstart = "#do-not-start"	 	
	$mhs = "MAX_HEAP_SIZE="
	$hns = "HEAP_NEWSIZE="
	$itok = "initial_token:" 
	$cname = "cluster_name:"
	$abst = "auto_bootstrap:"
	$seeds = "seeds:"
	$listenaddr = "listen_address:"
	$nodeid = str2int(regsubst($scoold::nodename,'^(\w+)(\d+)$','\2')) - 1
	$tokens = ["0", "56713727820156410577229101238628035242", "113427455640312821154458202477256070485"]
		
	user { $cassandrausr:
		home => $cassandrahome,
		managehome => true,
		shell => "/bin/bash"
	}
	
	exec { "nodetool-drain":
		command => "${casdir}/bin/nodetool -h localhost drain",
		onlyif => "test -e ${cassandrahome}/cassandra.pid",
		before => Exec["stop-cassandra"]
	}
		
	exec { "stop-cassandra":
		command => "stop cassandra && rm ${cassandrahome}/cassandra.pid",
		onlyif => "test -e ${cassandrahome}/cassandra.pid",
		before => User[$cassandrausr]
	}
	
	if $scoold::dbupgrade {
		exec{			
			"download-cassandra":
				command => "sudo -u ${cassandrausr} wget --no-check-certificate -O ${caspath} ${scoold::caslink}",
				require => User[$cassandrausr],
				before => Exec["unzip-cassandra"];
			"rename-old-cassandra":
				command => "mv ${casdir} ${cassandrahome}/cassandra_${scoold::casver}",
				onlyif => "test -e ${cassandrahome}/cassandra",
				require => Exec["download-cassandra"],
				before => Exec["unzip-cassandra"];
			"unzip-cassandra":
				command => "sudo -u ${cassandrausr} tar x -C ${cassandrahome} -f ${caspath}",
				before => Exec["rename-cassandra"];	
			"rename-cassandra":
				command => "mv ${cassandrahome}/apache-cassandra* ${casdir}",
				require => Exec["download-cassandra"];
			"set-cluster-name": 
				command => "sed -e '1,/${cname}/ s/${cname}.*/${cname} ${scoold::dbcluster}/' -i.bak ${casconf}",
				require => Exec["rename-cassandra"],
				before => Exec["start-cassandra"];
			"set-listen-address": 
				command => "sed -e '1,/${listenaddr}/ s/${listenaddr}.*/${listenaddr} ${ipaddress}/' -i.bak ${casconf}",
				require => Exec["rename-cassandra"],
				before => Exec["start-cassandra"];
			"download-jna":
				command => "sudo -u ${cassandrausr} wget --no-check-certificate -O ${casdir}/lib/jna.jar ${scoold::jnalink}",
				require => Exec["rename-cassandra"],
				before => Exec["start-cassandra"];
		}
		
		# if not in production JVM MEM values are auto calculated and set by cassandra env script
		if $scoold::inproduction {		
			$cmd1 = "'1,/#${mhs}/ s/#${mhs}.*/${mhs}\"${scoold::dbheapsize}\"/'"
			$cmd2 = "'1,/#${hns}/ s/#${hns}.*/${hns}\"${scoold::dbheapnew}\"/'"

			exec { "set-env": 
				command => "sed -e ${cmd1} -e ${cmd2} -i.bak ${casdir}/conf/cassandra-env.sh",
				require => Exec["rename-cassandra"],
				before => Exec["start-cassandra"]
			}
		}
		
		line { 
			"limits.conf1":
				ensure => present,		
				file => "/etc/security/limits.conf",
				line => "${cassandrausr} soft memlock unlimited";
			"limits.conf2":
				ensure => present,		
				file => "/etc/security/limits.conf",
				line => "${cassandrausr} hard memlock unlimited"
		}
	}
		
	exec { 
#		"enable-start": 
#			command => "sed -e 's/${dontstart}//' -i.bak ${casconf}",
#			require => Exec["set-cluster-name"];
#		"disable-start": 
#			command => "echo '${dontstart}' >> ${casconf}",
#			require => Exec["set-cluster-name"];
		"set-token":
        	command => "sed -e '1,/${itok}/ s/${itok}.*/${itok} ${tokens[$nodeid]}/' -i.bak ${casconf}",
	        require => Exec["set-cluster-name"];
		"set-seeds":
			command => "sed -e '1,/${seeds}/ s/${seeds}.*/${seeds} \"${scoold::dbseeds}\"/' -i.bak ${casconf}",
			require => Exec["set-cluster-name"];
	}
		
	if $nodeid == 0 {
		# first node is also the munin server
		package { ["munin", "nginx"]: }
		
		file { "/etc/nginx/sites-enabled/default": 
			ensure => absent,
			before => Exec["restart-nginx"]
		}
		
		file { "/etc/nginx/sites-enabled/munin":
			ensure => file,
			source => "puppet:///modules/scoold/munin.nginx.txt",
			owner => root,
			mode => 777,
			require => [Package["munin"], Package["nginx"]],
			before => Exec["restart-nginx"]
		}
		
		exec { "restart-nginx":
			command => "service nginx restart"
		}
	} else {
		exec { "set-autobootstrap":
			command => "sed -e '1,/${abst}/ s/${abst}.*/${abst} true/' -i.bak ${casconf}",
			require => Exec["set-cluster-name"];	
		}
	}	
		
	$cmpdir = "/home/${scoold::defuser}/cassandra-munin-plugins"
	$cmd1 = "chmod -R 777 ${cmpdir}"
	$cmd2 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/jvm_memory"
	$cmd3 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/ops_pending"
	 
#		$cmd4 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/ops_pending"
#		$cmd5 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/ops_pending"
#		$cmd6 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/ops_pending"
#		$cmd7 = "ln -sf ${cmpdir}/jmx_ /etc/munin/plugins/ops_pending"
	
	$cmdr = "service munin-node restart"
	
	file { $cmpdir:
		source => "puppet:///modules/scoold/cassandra-munin-plugins",
		recurse => true,
		force => true,
		before => Exec["install-cassandra-munin-plugin"]			
	}
	
	exec { "install-cassandra-munin-plugin":
		command => "$cmd1 && $cmd2 && $cmd3 && $cmdr"			
	}
	
	file { ["/var/lib/cassandra", "/var/lib/cassandra/saved_caches", "/var/lib/cassandra/commitlog", 
			"/var/lib/cassandra/data", "/var/log/cassandra"]:
		ensure => directory,
	    owner => $cassandrausr,
	    group => $cassandrausr,
	    recurse => true,
	    mode => 755,
	    require => Exec["rename-cassandra"]
	}
	
	file { "/etc/init/cassandra.conf":
		ensure => file,
		source => "puppet:///modules/scoold/cassandra.conf",
		owner => root,
		mode => 644,
		before => Exec["start-cassandra"]
	}
		
	exec { "start-cassandra":
		command => "start cassandra",
		unless => "grep '${dontstart}' ${casconf} 2>/dev/null", # "test -e ${cassandrahome}/cassandra.pid", 
		require => Exec["set-cluster-name"]
	}			
}