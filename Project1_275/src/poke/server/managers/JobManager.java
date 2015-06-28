/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.managers;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.resources.JobResource;
import poke.server.ServerInitializer;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementInitializer;
import poke.server.queue.ChannelQueue;
import poke.server.queue.QueueFactory;
import poke.server.resources.ResourceUtil;
import eye.Comm.Header;
import eye.Comm.JobBid;
import eye.Comm.JobDesc;
import eye.Comm.JobDesc.JobCode;
import eye.Comm.JobOperation;
import eye.Comm.JobOperation.JobAction;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.JobProposal;
import eye.Comm.JobStatus;
import eye.Comm.Management;
import eye.Comm.MgmtHeader;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoPayload;
import eye.Comm.PokeStatus;
import eye.Comm.Request;

/**
 * The job manager class is used by the system to hold (enqueue) jobs and can be
 * used in conjunction to the voting manager for cooperative, de-centralized job
 * scheduling. This is used to ensure leveling of the servers take into account
 * the diversity of the network.
 * 
 * @author gash
 * 
 */
public class JobManager {
	protected static Logger logger = LoggerFactory.getLogger("JobManager");
	protected static AtomicReference<JobManager> instance = new AtomicReference<JobManager>();

	private static ServerConf conf;

	// declaring hashmaps and queue to save job bid values and channel
	private static Map<String, JobBid> jobBidMap;
	private Map<String, Channel> channelHashMap = new HashMap<String, Channel>();
	LinkedBlockingDeque<JobBid> jobBidQueue;

	// defining namespace variable values
	String read = "read";
	String write = "write";
	String delete = "delete";

	public static JobManager initManager(ServerConf conf) {
		JobManager.conf = conf;
		instance.compareAndSet(null, new JobManager());
		return instance.get();
	}

	public void addToChannelMap(String jobId, Channel ch) {
		channelHashMap.put(jobId, ch);
	}

	public static JobManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	public JobManager() {
		// defining Hash map and queues
		this.jobBidMap = new HashMap<String, JobBid>();
		jobBidQueue = new LinkedBlockingDeque<JobBid>();
	}

	/**
	 * a new job request has been sent out that I need to evaluate if I can run
	 * it
	 * 
	 * @param req
	 *            The management request
	 */
	public void processRequest(Management mgmt) {
		JobProposal req = mgmt.getJobPropose();
		if (req == null)
			return;

	}

	/**
	 * a job bid for my job
	 * 
	 * @param request
	 *            The bid
	 */
	public void processRequest(JobBid request) {
		logger.info("New Job Bid received: " + request.getBid());

		// get the leader
		Integer leaderNode = ElectionManager.getInstance().whoIsTheLeader();

		// check for leader if it matches with configuration
		if (leaderNode != null && leaderNode.equals(conf.getNodeId())) {
			if (jobBidMap.containsKey(request.getJobId())) {
				// if bitMap contains job ID then do not process
				return;
			}
			if (request.getBid() == 1) {
				// if bid is 1 add to bidQueue
				jobBidQueue.add(request);
				jobBidMap.put(request.getJobId(), request);
			}
			if (request.getBid() == 1) {
				// get the owner ID from request
				int toNode = (int) request.getOwnerId();
				// initialize hashmap for job request
				Map<String, Request> reqMap = JobResource.getReqMap();
				Request req = reqMap.get(request.getJobId());
				// To-Do condition should be changed according to other cluster
				// common standards
				if (request.getNameSpace().equals("")) {
					Request.Builder rb = Request.newBuilder(req);
					PhotoPayload.Builder ppb = PhotoPayload.newBuilder();
					PhotoPayload photoPay = req.getBody().getPhotoPayload();
					ppb.setName(photoPay.getName());
					ppb.setData(photoPay.getData());

					// create header builder and set values
					Header.Builder hb = rb.getHeaderBuilder();
					hb.setToNode(toNode);
					rb.setHeader(hb.build());
					hb.setRoutingId(Header.Routing.JOBS);

					Payload.Builder pb = rb.getBodyBuilder();
					JobOperation.Builder jobOpBuild = pb.getJobOpBuilder();
					jobOpBuild.setAction(JobAction.ADDJOB);

					JobDesc.Builder jd = JobDesc.newBuilder();
					jd.setJobId(request.getJobId());
					jd.setOwnerId(request.getOwnerId());
					jd.setNameSpace("");
					jd.setStatus(JobCode.JOBQUEUED);
					jobOpBuild.setData(jd.build());

					pb.setJobOp(jobOpBuild.build());
					rb.setBody(pb.build());
					Request jobDispatched = rb.build();

					// connect to channel and forward the request
					Channel channel = channelHashMap.get(request.getJobId());
					InetSocketAddress isa = (InetSocketAddress) channel
							.remoteAddress();
					String hostname = null;
					if (isa != null)
						hostname = isa.getAddress().getHostAddress();
					channelHashMap.remove(request.getJobId());
					//added according to other cluster port
					int port = 5572;
					logger.info("Hostname got from channel" + hostname);
					InetSocketAddress sa = new InetSocketAddress(hostname, port);
					Channel out = connectOtherCluster(sa);
					ChannelQueue queue = QueueFactory.getInstance(out);
					logger.info("Job request has been forwarded to leader: ");
					queue.enqueueResponse(jobDispatched, out);
				} else {
					// create request and header builder to set response
					Request.Builder rb = Request.newBuilder(req);
					Header.Builder hb = rb.getHeaderBuilder();
					hb.setRoutingId(Header.Routing.JOBS);
					hb.setToNode(toNode);
					rb.setHeader(hb.build());
					Request jobForward = rb.build();
					// get the adjacent node for slave
					NodeDesc slave = conf.getAdjacent().getAdjacentNodes()
							.get(toNode);

					// make socket connection with slave
					InetSocketAddress isa = new InetSocketAddress(
							slave.getHost(), slave.getPort());
					Channel channel = connectOtherCluster(isa);
					ChannelQueue cq = QueueFactory.getInstance(channel);
					logger.info("Job request has been forwarded to slave node: "
							+ toNode);
					cq.enqueueResponse(jobForward, channel); // send to slave
																// node
				}
			}
		}
	}

	/**
	 * a new job proposal has been sent out that I need to evaluate if I can run
	 * it
	 * 
	 * @param request
	 *            The proposal
	 */
	public void processRequest(JobProposal request) {
		if (request == null)
			return;

		// get the leader
		int leaderNode = ElectionManager.getInstance().whoIsTheLeader();
		logger.info("Server received Job Proposal Id:" + request.getJobId());

		// building the management builder, management header, job builder
		Management.Builder mb = Management.newBuilder();
		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		JobBid.Builder jb = JobBid.newBuilder();

		// add values to management header
		mhb.setTime(System.currentTimeMillis());
		mhb.setOriginator(conf.getNodeId());

		// add values to job builder
		jb.setJobId(request.getJobId());
		jb.setOwnerId(conf.getNodeId());
		jb.setNameSpace(request.getNameSpace());
		// get random bid and set the bid to job builder
		int bid = (int) Math.floor(Math.random() + 0.5);
		jb.setBid(bid);

		// add values to management builder
		mb.setHeader(mhb.build());
		mb.setJobBid(jb.build());
		// prepare mangement variable to be sent to channel
		Management jobBid = mb.build();

		// checks for namespace values to send job bid
		// if correct then send to its channel
		if (request.getNameSpace().equals("read")
				|| request.getNameSpace().equals(write)
				|| request.getNameSpace().equals(delete)) {
			Channel ch = channelHashMap.get(request.getJobId());
			ch.writeAndFlush(jobBid);
			channelHashMap.remove(request.getJobId()); // remove after sending
														// the
														// job bid
			logger.info("Job BID has been requested with Job ID: "
					+ request.getJobId());
		} else { // if not correct then send to leader
			NodeDesc leader = conf.getAdjacent().getAdjacentNodes()
					.get(leaderNode);
			InetSocketAddress sa = new InetSocketAddress(leader.getHost(),
					leader.getMgmtPort());
			Channel ch = connectManagement(sa);
			ch.writeAndFlush(jobBid);
			logger.info("Job BID has been requested with Job ID: "
					+ request.getJobId());
		}
	}

	/**
	 * connect to management through socket address
	 * 
	 * @param isa
	 *            InetSocketAddress
	 */
	public Channel connectManagement(InetSocketAddress isa) {
		EventLoopGroup group = new NioEventLoopGroup();
		ChannelFuture channelFuture = null;

		// initialize server and make connection
		try {
			ManagementInitializer mi = new ManagementInitializer(false);
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(mi);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			channelFuture = b.connect(isa);
			channelFuture.awaitUninterruptibly(5000l);
		} catch (Exception ex) {
			logger.debug("connection intialize failed");
		}
		if (channelFuture != null && channelFuture.isDone()
				&& channelFuture.isSuccess())
			return channelFuture.channel(); // if success then return channel
		else
			throw new RuntimeException("connection to server failed");
	}

	/**
	 * connect to other cluster through socket address
	 * 
	 * @param isa
	 *            InetSocketAddress
	 */
	public Channel connectOtherCluster(InetSocketAddress isa) {
		EventLoopGroup group = new NioEventLoopGroup();
		ChannelFuture cf = null;
		// initialize server and make connection
		try {
			ServerInitializer si = new ServerInitializer(false);
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(si);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			// connect to socket and waits for connection
			cf = b.connect(isa);
			cf.awaitUninterruptibly(5000l);
		} catch (Exception ex) {
			logger.debug("connection intialize failed");
		}
		if (cf != null && cf.isDone() && cf.isSuccess())
			return cf.channel(); // if success then return channel
		else
			throw new RuntimeException("connection to server failed");
	}

}
