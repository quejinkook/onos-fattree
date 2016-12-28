#!/usr/bin/python

"""
Create Fat-Tree topology, and connect to remote controller
Default pod number is 4.
"""

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.node import OVSSwitch, RemoteController,OVSKernelSwitch
from mininet.topo import Topo
from mininet.net import Mininet
from mininet.log import setLogLevel, info
from functools import partial
def fatTree(k=4):
	"""
	Create Fat-Tree topology, and connect to remote controller
	In this implementation, we set K and controller IP as static parameter.

	TODO: get K and controller IP from user.
	"""
	
	net = Mininet( ipBase='192.168.0.0/24')
	# add remote controller. 
	c0=net.addController(name='c0',controller=RemoteController,ip='192.168.0.10',protocol='tcp',port=6653)
	
	switch_c_list=[]
	# calculate core swtich count
	count = (k/2)*(k/2)
	for i in range(0,count):
		# create switch
		switch_c = net.addSwitch("c%02d%02d%02d"%(k,i/(k/2)+1,i%(k/2)+1),dpid="0000000000%02d%02d%02d"%(k,i/(k/2)+1,i%(k/2)+1),cls=OVSKernelSwitch,protocols='OpenFlow13')
		switch_c_list.append(switch_c)
	
	# looping for pod
	for i in range(0,k):
		switch_a_list=[]
		# create aggresive switch
		for j in range(0,k/2):
			switch_a = net.addSwitch('a%02d%02d01'%(i,j+k/2),dpid="0000000000%02d%02d01"%(i,j+k/2),cls=OVSKernelSwitch,protocols='OpenFlow13')
			switch_a_list.append(switch_a)
		# create edge swtich
		for j in range(0,k/2):
			switch_e = net.addSwitch('e%02d%02d01'%(i,j),dpid="0000000000%02d%02d01"%(i,j),cls=OVSKernelSwitch,protocols='OpenFlow13')
			# add host
			for m in range(0,k/2):
				host = net.addHost('h%d%d%d'%(i,j,m+2),ip="10.%d.%d.%d"%(i,j,m+2))
				#add host - edge link
				net.addLink(host, switch_e)
			# add edge - aggresive link
			for m in range(0,k/2):
				net.addLink( switch_e,switch_a_list[m])
			# done for edge link
			# done for host link

		for j in range(0,k/2):
			switch_a = switch_a_list[j]
			for m in range(0,k/2): 
				net.addLink(switch_a,switch_c_list[j*k/2+m])
			# done for aggresive link
			# done for core link

	# build network
	net.build()
	# start all controllers
	for controller in net.controllers:
		controller.start()

	info( '*** Starting switches\n')	
	
	# start all switches and connect to remote controller
	for switch in net.switches:
		switch.start([c0])
	# start mininet CLI
	CLI(net)

	#stop network
	net.stop()

if __name__ == '__main__':
	setLogLevel( 'info' )
	# call function
	fatTree(k=4)
