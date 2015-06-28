/*
 * copyright 2012, gash
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
package poke.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import poke.server.ServerInitializer;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementInitializer;
import poke.server.managers.ElectionManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;
import eye.Comm.JobOperation;
import eye.Comm.JobProposal;
import eye.Comm.Management;
import eye.Comm.MgmtHeader;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.Ping;
import eye.Comm.PokeStatus;
import eye.Comm.Request;
import poke.server.storage.sharding.DatabaseSharding;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;

public class JobResource implements Resource {
	protected static Logger logger = LoggerFactory.getLogger("JobResource");

	// defining variables to be used for job read
	boolean jobStatus = false;
	ByteString imageDataStringReply = null;
	String newImageName = null;

	// defining namespace variable values
	String read = "read";
	String write = "write";
	String delete = "delete";

	// declaring Hashmaps for job request and Channel
	private static Map<String, Request> reqMap = new HashMap<String, Request>();
	private static Map<String, Channel> channelMap = new HashMap<String, Channel>();

	private static ServerConf configFile;

	public JobResource() {
	}

	@Override
	public void setConfiguration(ServerConf conf) {
		configFile = conf;
	}

	@Override
	public Request process(Request request) {
		// TODO Auto-generated method stub

		Request reply = null;
		// get the leader node value
		int leaderNode = ElectionManager.getInstance().whoIsTheLeader();

		if (configFile.getNodeId() == leaderNode) {
			if (request.getBody().hasJobStatus()) {
				logger.info("Job Status received ");
				String jobId = request.getBody().getJobStatus().getJobId();
				logger.info("Job ID of Job Status " + jobId);
				if (reqMap.containsKey(jobId)) {
					reqMap.remove(jobId);
					Channel ch = channelMap.get(jobId);
					channelMap.remove(jobId);
					ch.writeAndFlush(request);
				}
			} else if (request.getBody().hasJobOp()) {
				logger.info("A new job request has been received by Leader");
				PhotoHeader photoHead = request.getHeader().getPhotoHeader();
				// Job proposal
				JobOperation jobOp = request.getBody().getJobOp();
				logger.info("Job Operation ID: " + jobOp.getJobId());
				reqMap.put(jobOp.getJobId(), request);

				PhotoPayload photoPay = request.getBody().getPhotoPayload();

				Management.Builder mb = Management.newBuilder();
				JobProposal.Builder jbr = JobProposal.newBuilder();
				PhotoPayload.Builder ppb = PhotoPayload.newBuilder();

				// setting mgmt header
				MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
				mhb.setOriginator(configFile.getNodeId());
				mhb.setTime(System.currentTimeMillis());

				jbr.setJobId(jobOp.getJobId());
				jbr.setNameSpace(jobOp.getData().getNameSpace());
				jbr.setOwnerId(jobOp.getData().getOwnerId());
				jbr.setWeight(5);
				jbr.setOptions(jobOp.getData().getOptions());

				ppb.setName(photoPay.getName());
				ppb.setData(photoPay.getData());

				mb.setHeader(mhb.build());
				mb.setJobPropose(jbr.build());
				Management jobProposal = mb.build();
				String destHost = null;
				int destPort = 0;

				// TO-DO : need to update condition according to Team leads
				// communication standards
				if (photoHead.hasEntryNode()) {					
					// Arraylist of IP addresses of leaders of other clusters
					List<String> leaderList = new ArrayList<String>();
					 leaderList.add(new String("192.168.1.15:8080"));
					 leaderList.add(new String("192.168.1.31:5573"));
					 leaderList.add(new String("192.168.1.153:5570"));
					for (String destination : leaderList) {
						String[] dest = destination.split(":");
						destHost = dest[0];						
						destPort = Integer.parseInt(dest[1]);
						logger.info("Job proposal forwarded to other cluster : " + destHost + ":" + destPort);
						InetSocketAddress sa = new InetSocketAddress(destHost,
								destPort);
						Channel ch = connectToManagement(sa);
						ch.writeAndFlush(jobProposal);
						logger.info("Job proposal has been sent to other cluster");
					}
				} else {
					for (NodeDesc nn : configFile.getAdjacent()
							.getAdjacentNodes().values()) {
						destHost = nn.getHost();
						destPort = nn.getMgmtPort();
						InetSocketAddress sa = new InetSocketAddress(destHost,
								destPort);
						Channel ch = connectToManagement(sa);
						ch.writeAndFlush(jobProposal);
						logger.info("Job Proposal as been sent");
					}
				}
			}
		}

		else {
			String requestType = request.getHeader().getPhotoHeader()
					.getRequestType().toString();
			logger.info("Job Operation received: " + requestType);
			// getting image name and content
			String imageName = request.getBody().getPhotoPayload().getName();

			try {
				// connecting to database
				DatabaseSharding dbShard = new DatabaseSharding();
				// add host of Database
				//To-Do Update IP address to Database IP
				dbShard.initManager("127.0.0.1", 27017, "test");

				if (requestType == "read") {

					logger.info("Server has read request for image: "
							+ imageName);

					// call function to save image in DB
					File receiveImage;
					FileInputStream imageInFile = null;
					try {
						receiveImage = dbShard.getImage(imageName);

						imageInFile = new FileInputStream(receiveImage);
						byte imageData[] = new byte[(int) receiveImage.length()];
						imageInFile.read(imageData);

						// Converting Image byte array into Base64 String
						String imageDataString = Base64
								.encodeBase64URLSafeString(imageData);
						imageDataStringReply = ByteString.copyFrom(imageData);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
						imageInFile.close();
					}
					// set job status to true
					jobStatus = true;
				} else if (requestType == "write") {
					logger.info("Server has received image: " + imageName);

					ByteString imageByteString = request.getBody()
							.getPhotoPayload().getData();
					FileOutputStream imageOutFile = null;
					byte[] encoded = imageByteString.toByteArray();
					// decoding to convert into image
					byte[] imageDataBytes = Base64.decodeBase64(encoded);

					// Write a image byte array into file system
					imageOutFile = new FileOutputStream(imageName);
					imageOutFile.write(imageDataBytes);

					// call function to save image in DB
					try {
						newImageName = dbShard.saveImage(imageName);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
						imageOutFile.close();
					}
					// set job status to true
					jobStatus = true;

				} else if (requestType == "delete") {
					logger.info("Server has been requested to delete image: "
							+ imageName);
					// call function to save image in DB
					try {
						jobStatus = dbShard.removeImage(imageName);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					logger.info("Client request can not be recognized");
					jobStatus = false;
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			Request.Builder rb = Request.newBuilder();
			// check for job status and prepare Header
			if (jobStatus) {
				// metadata
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						PokeStatus.SUCCESS, null, ResponseFlag.success));

			} else {
				// metadata
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						PokeStatus.FAILURE, null, ResponseFlag.failure));
			}

			// payload
			Payload.Builder pb = Payload.newBuilder();
			Ping.Builder fb = Ping.newBuilder();
			fb.setTag(request.getBody().getPing().getTag());
			fb.setNumber(request.getBody().getPing().getNumber());
			pb.setPing(fb.build());

			PhotoPayload.Builder ppb = PhotoPayload.newBuilder();
			if (imageDataStringReply != null) {
				ppb.setData(imageDataStringReply);
			}
			if (newImageName != null) {
				ppb.setName(newImageName);
			}
			pb.setPhotoPayload(ppb.build());
			rb.setBody(pb.build());
			reply = rb.build();
		}
		return reply;
	}

	/**
	 * return the channel Map
	 * 
	 * @param String
	 *            , Channel
	 */
	public static Map<String, Channel> getChMap() {
		return channelMap;
	}

	/**
	 * return the job Request Map
	 * 
	 * @param String
	 *            , Request
	 */
	public static Map<String, Request> getReqMap() {
		return reqMap;
	}

	/**
	 * adds jobId and channel to Channel Map
	 * 
	 * @param String
	 *            , Channel
	 */
	public static void addToChannelMap(String jobId, Channel channel) {
		JobResource.channelMap.put(jobId, channel);
	}

	/**
	 * connect to management through socket address
	 * 
	 * @param isa
	 *            InetSocketAddress
	 */
	public Channel connectToManagement(InetSocketAddress isa) {
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
		// Start the connection attempt.
		ChannelFuture channelFuture = null;
		EventLoopGroup group = new NioEventLoopGroup();

		try {
			ServerInitializer si = new ServerInitializer(false);
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(si);
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

	public void writeToOutputStream(String stream) {

	}

}
