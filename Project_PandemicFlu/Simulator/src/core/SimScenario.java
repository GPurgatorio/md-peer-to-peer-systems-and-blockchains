/*
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import input.EventQueue;
import input.EventQueueHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.PandemicFluRoutine;
import movement.map.SimMap;
import routing.MessageRouter;

/**
 * A simulation scenario used for getting and storing the settings of a
 * simulation run.
 */
public class SimScenario implements Serializable {

	/** a way to get a hold of this... */
	private static SimScenario myinstance=null;

	/** namespace of scenario settings ({@value})*/
	public static final String SCENARIO_NS = "Scenario";
	/** number of host groups -setting id ({@value})*/
	public static final String NROF_GROUPS_S = "nrofHostGroups";
	/** number of interface types -setting id ({@value})*/
	public static final String NROF_INTTYPES_S = "nrofInterfaceTypes";
	/** scenario name -setting id ({@value})*/
	public static final String NAME_S = "name";
	/** end time -setting id ({@value})*/
	public static final String END_TIME_S = "endTime";
	/** update interval -setting id ({@value})*/
	public static final String UP_INT_S = "updateInterval";
	/** simulate connections -setting id ({@value})*/
	public static final String SIM_CON_S = "simulateConnections";

	/** namespace for interface type settings ({@value}) */
	public static final String INTTYPE_NS = "Interface";
	/** interface type -setting id ({@value}) */
	public static final String INTTYPE_S = "type";
	/** interface name -setting id ({@value}) */
	public static final String INTNAME_S = "name";

	/** namespace for application type settings ({@value}) */
	public static final String APPTYPE_NS = "Application";
	/** application type -setting id ({@value}) */
	public static final String APPTYPE_S = "type";
	/** setting name for the number of applications */
	public static final String APPCOUNT_S = "nrofApplications";

	/** namespace for host group settings ({@value})*/
	public static final String GROUP_NS = "Group";
	/** group id -setting id ({@value})*/
	public static final String GROUP_ID_S = "groupID";
	/** number of hosts in the group -setting id ({@value})*/
	public static final String NROF_HOSTS_S = "nrofHosts";
	/** movement model class -setting id ({@value})*/
	public static final String MOVEMENT_MODEL_S = "movementModel";
	/** router class -setting id ({@value})*/
	public static final String ROUTER_S = "router";
	/** number of interfaces in the group -setting id ({@value})*/
	public static final String NROF_INTERF_S = "nrofInterfaces";
	/** interface name in the group -setting id ({@value})*/
	public static final String INTERFACENAME_S = "interface";
	/** application name in the group -setting id ({@value})*/
	public static final String GAPPNAME_S = "application";

	/** package where to look for movement models */
	private static final String MM_PACKAGE = "movement.";
	/** package where to look for router classes */
	private static final String ROUTING_PACKAGE = "routing.";

	/** package where to look for interface classes */
	private static final String INTTYPE_PACKAGE = "interfaces.";

	/** package where to look for application classes */
	private static final String APP_PACKAGE = "applications.";

	/** The world instance */
	private World world;
	/** List of hosts in this simulation */
	protected List<DTNHost> hosts;
	/** Name of the simulation */
	private String name;
	/** number of host groups */
	int nrofGroups;
	/** Width of the world */
	private int worldSizeX;
	/** Height of the world */
	private int worldSizeY;
	/** Largest host's radio range */
	private double maxHostRange;
	/** Simulation end time */
	private double endTime;
	/** Update interval of sim time */
	private double updateInterval;
	/** External events queue */
	private EventQueueHandler eqHandler;
	/** Should connections between hosts be simulated */
	private boolean simulateConnections;
	/** Map used for host movement (if any) */
	private SimMap simMap;

	/** Global connection event listeners */
	private List<ConnectionListener> connectionListeners;
	/** Global message event listeners */
	private List<MessageListener> messageListeners;
	/** Global movement event listeners */
	private List<MovementListener> movementListeners;
	/** Global update event listeners */
	private List<UpdateListener> updateListeners;
	/** Global application event listeners */
	private List<ApplicationListener> appListeners;

	public static List<Coord> hotspots;

	static {
		DTNSim.registerForReset(SimScenario.class.getCanonicalName());
		reset();
	}

	public static void reset() {
		myinstance = null;
	}

	/**
	 * Creates a scenario based on Settings object.
	 */
	protected SimScenario() {
		Settings s = new Settings(SCENARIO_NS);
		nrofGroups = s.getInt(NROF_GROUPS_S);

		this.name = s.valueFillString(s.getSetting(NAME_S));
		this.endTime = s.getDouble(END_TIME_S);
		this.updateInterval = s.getDouble(UP_INT_S);
		this.simulateConnections = s.getBoolean(SIM_CON_S);

		s.ensurePositiveValue(nrofGroups, NROF_GROUPS_S);
		s.ensurePositiveValue(endTime, END_TIME_S);
		s.ensurePositiveValue(updateInterval, UP_INT_S);

		this.simMap = null;
		this.maxHostRange = 1;

		this.connectionListeners = new ArrayList<ConnectionListener>();
		this.messageListeners = new ArrayList<MessageListener>();
		this.movementListeners = new ArrayList<MovementListener>();
		this.updateListeners = new ArrayList<UpdateListener>();
		this.appListeners = new ArrayList<ApplicationListener>();
		this.eqHandler = new EventQueueHandler();

		/* TODO: check size from movement models */
		s.setNameSpace(MovementModel.MOVEMENT_MODEL_NS);
		int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.worldSizeX = worldSize[0];
		this.worldSizeY = worldSize[1];

		createHosts();

		this.world = new World(hosts, worldSizeX, worldSizeY, updateInterval,
				updateListeners, simulateConnections,
				eqHandler.getEventQueues());
	}

	/**
	 * Returns the SimScenario instance and creates one if it doesn't exist yet
	 */
	public static SimScenario getInstance() {
		if (myinstance == null) {
			myinstance = new SimScenario();
		}
		return myinstance;
	}



	/**
	 * Returns the name of the simulation run
	 * @return the name of the simulation run
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns true if connections should be simulated
	 * @return true if connections should be simulated (false if not)
	 */
	public boolean simulateConnections() {
		return this.simulateConnections;
	}

	/**
	 * Returns the width of the world
	 * @return the width of the world
	 */
	public int getWorldSizeX() {
		return this.worldSizeX;
	}

	/**
	 * Returns the height of the world
	 * @return the height of the world
	 */
	public int getWorldSizeY() {
		return worldSizeY;
	}

	/**
	 * Returns simulation's end time
	 * @return simulation's end time
	 */
	public double getEndTime() {
		return endTime;
	}

	/**
	 * Returns update interval (simulated seconds) of the simulation
	 * @return update interval (simulated seconds) of the simulation
	 */
	public double getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * Returns how long range the hosts' radios have
	 * @return Range in meters
	 */
	public double getMaxHostRange() {
		return maxHostRange;
	}

	/**
	 * Returns the (external) event queue(s) of this scenario or null if there
	 * aren't any
	 * @return External event queues in a list or null
	 */
	public List<EventQueue> getExternalEvents() {
		return this.eqHandler.getEventQueues();
	}

	/**
	 * Returns the SimMap this scenario uses, or null if scenario doesn't
	 * use any map
	 * @return SimMap or null if no map is used
	 */
	public SimMap getMap() {
		return this.simMap;
	}

	/**
	 * Adds a new connection listener for all nodes
	 * @param cl The listener
	 */
	public void addConnectionListener(ConnectionListener cl){
		this.connectionListeners.add(cl);
	}

	/**
	 * Adds a new message listener for all nodes
	 * @param ml The listener
	 */
	public void addMessageListener(MessageListener ml){
		this.messageListeners.add(ml);
	}

	/**
	 * Adds a new movement listener for all nodes
	 * @param ml The listener
	 */
	public void addMovementListener(MovementListener ml){
		this.movementListeners.add(ml);

		// Invoke the initialLocation() for all nodes that already exist in
		// the Scenario. This ensures initialLocation() gets called for every
		// node.
		for (final DTNHost host : this.hosts) {
			ml.initialLocation(host, host.getLocation());
		}
	}

	/**
	 * Adds a new update listener for the world
	 * @param ul The listener
	 */
	public void addUpdateListener(UpdateListener ul) {
		this.updateListeners.add(ul);
	}

	/**
	 * Returns the list of registered update listeners
	 * @return the list of registered update listeners
	 */
	public List<UpdateListener> getUpdateListeners() {
		return this.updateListeners;
	}

	/**
	 * Adds a new application event listener for all nodes.
	 * @param al The listener
	 */
	public void addApplicationListener(ApplicationListener al) {
		this.appListeners.add(al);
	}

	/**
	 * Returns the list of registered application event listeners
	 * @return the list of registered application event listeners
	 */
	public List<ApplicationListener> getApplicationListeners() {
		return this.appListeners;
	}

	/**
	 * Creates hosts for the scenario
	 */
	protected void createHosts() {
		this.hosts = new ArrayList<DTNHost>();
		this.hotspots = new ArrayList<Coord>();

		Settings OS = new Settings("PandemicFlu");
		int numOfOS = OS.getInt("nrOfOS");

		if(OS.getInt("nrOfPois") < 0 || numOfOS < 0) {
			System.err.println("Invalid config: nrOfPois or numOfOS must be positive values!");
			System.exit(-1);
		}

		String[] OS_Names = new String[numOfOS];
		for(int i=0; i < numOfOS; i++) {
			OS_Names[i] = OS.getSetting("operatingSystem" + i);
		}

		double[] OS_Distr = new double[numOfOS];
		for(int i=0; i < numOfOS; i++) {
			OS_Distr[i] = OS.getDouble("percOS" + i);
			if(OS_Distr[i] < 0 || OS_Distr[i] > 1) {
				System.err.println("Error in configurations. OS" + i + "_Distr isn't a valid value [0,1]");
				System.exit(-1);
			}
		}

		checkSumProb(OS_Distr);

		for (int i=1; i<=nrofGroups; i++) {
			List<NetworkInterface> interfaces =
				new ArrayList<NetworkInterface>();
			Settings s = new Settings(GROUP_NS+i);
			s.setSecondaryNamespace(GROUP_NS);
			String gid = s.getSetting(GROUP_ID_S);
			int nrofHosts = s.getInt(NROF_HOSTS_S);
			int nrofInterfaces = s.getInt(NROF_INTERF_S);
			int appCount;

			// creates prototypes of MessageRouter and MovementModel
			MovementModel mmProto =
				(MovementModel)s.createIntializedObject(MM_PACKAGE +
						s.getSetting(MOVEMENT_MODEL_S));
			MessageRouter mRouterProto =
				(MessageRouter)s.createIntializedObject(ROUTING_PACKAGE +
						s.getSetting(ROUTER_S));

			/* checks that these values are positive (throws Error if not) */
			s.ensurePositiveValue(nrofHosts, NROF_HOSTS_S);
			s.ensurePositiveValue(nrofInterfaces, NROF_INTERF_S);

			if(mmProto.toString().equals("PandemicFluRoutine")) {
				PandemicFluRoutine tmp = (PandemicFluRoutine) mmProto;
				List<Coord> pois = tmp.getPois();
				for(Coord c : pois) {
					hotspots.add(c);
				}
			}

			// setup interfaces
			for (int j=1;j<=nrofInterfaces;j++) {
				String intName = s.getSetting(INTERFACENAME_S + j);
				Settings intSettings = new Settings(intName);
				NetworkInterface iface =
					(NetworkInterface)intSettings.createIntializedObject(
							INTTYPE_PACKAGE +intSettings.getSetting(INTTYPE_S));
				iface.setClisteners(connectionListeners);
				iface.setGroupSettings(s);
				interfaces.add(iface);
			}

			// setup applications
			if (s.contains(APPCOUNT_S)) {
				appCount = s.getInt(APPCOUNT_S);
			} else {
				appCount = 0;
			}
			for (int j=1; j<=appCount; j++) {
				String appname = null;
				Application protoApp = null;
				try {
					// Get name of the application for this group
					appname = s.getSetting(GAPPNAME_S+j);
					// Get settings for the given application
					Settings t = new Settings(appname);
					// Load an instance of the application
					protoApp = (Application)t.createIntializedObject(
							APP_PACKAGE + t.getSetting(APPTYPE_S));
					// Set application listeners
					protoApp.setAppListeners(this.appListeners);
					// Set the proto application in proto router
					//mRouterProto.setApplication(protoApp);
					mRouterProto.addApplication(protoApp);
				} catch (SettingsError se) {
					// Failed to create an application for this group
					System.err.println("Failed to setup an application: " + se);
					System.err.println("Caught at " + se.getStackTrace()[0]);
					System.exit(-1);
				}
			}

			if (mmProto instanceof MapBasedMovement) {
				this.simMap = ((MapBasedMovement)mmProto).getMap();
			}

			// creates hosts of ith group
			for (int j=0; j<nrofHosts; j++) {
				ModuleCommunicationBus comBus = new ModuleCommunicationBus();

				// prototypes are given to new DTNHost which replicates
				// new instances of movement model and message router
				DTNHost host = new DTNHost(this.messageListeners,
						this.movementListeners,	gid, interfaces, comBus,
						mmProto, mRouterProto, i);

				// Set the OS depending on given settings
				int idx = setOS(OS_Distr);
				host.setOS(OS_Names[idx]);

				hosts.add(host);
			}
		}
		if(numOfOS > hosts.size()) {
			System.err.println("Invalid config: there are more operatying systems than hosts.");
			System.exit(-1);
		}
		infectOne(hosts);
	}

	/**
	 * Returns the list of nodes for this scenario.
	 * @return the list of nodes for this scenario.
	 */
	public List<DTNHost> getHosts() {
		return this.hosts;
	}

	/**
	 * Returns the World object of this scenario
	 * @return the World object
	 */
	public World getWorld() {
		return this.world;
	}

	/**
	 * Simple infection of a random host
	 * @param hosts the list of hosts
	 */
	private void infectOne(List<DTNHost> hosts) {
		if (hosts.size() < 1)
			return;

		Collections.shuffle(hosts);

		Settings s = new Settings("PandemicFlu");
		for (int i = 0; i < s.getInt("nrOfOS"); i++) {
			DTNHost firstInfected;
			int j = 0;
			//System.out.println("Infecting " + s.getSetting("operatingSystem" + i));
			do {
				if(j >= hosts.size()) {
					firstInfected = null;
					break;
				}
				firstInfected = hosts.get(j);
				j++;
				//System.out.println(firstInfected.toString() + " -> " + firstInfected.getOS());
			} while(!firstInfected.getOS().equals(s.getSetting("operatingSystem" + i)));

			if(firstInfected != null) {
				firstInfected.becomeInfected();
				System.out.println("First infected for $ " +
						s.getSetting("operatingSystem" + i) + " is " + firstInfected.toString());
			}
			else
				System.out.println("\n**Warning**: there are no " + s.getSetting("operatingSystem" + i) + " hosts (" +
						"probabilistic assignment)\nConsider maybe putting a higher probability into this OS, put higher " +
						"num of hosts or just retry\nNote: simulation will still work, this is just a warning!\n");
		}
	}

	/**
	 * If the sum of given probabilities isn't equal to 1, it will terminate the program
	 *
	 * @param array the array to sum from
	 */
	private void checkSumProb(double[] array) {
		double sum = 0;
		for(int i = 0; i < array.length; i++) {
			sum += array[i];
		}

		if(sum != 1) {
			System.err.println("Sum of distributions for different OS isn't equal to 1! (" + sum + ")");
			System.exit(-1);
		}
	}

	/**
	 * Selects a random index according to the probabilities in the given array
	 *
	 * @param array the array with probabilities
	 * @return a random index, with probability according to values in array
	 */
	private int setOS(double[] array) {
		Random r = new Random();
		double rand = r.nextDouble();
		double check = 0;
		int index = -1;
		while(check < rand) {
			check += array[index + 1];
			index++;
		}
		return (index == -1 ? 0 : index);
	}

}
