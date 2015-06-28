package poke.server.storage.sharding;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.DBAddress;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import java.util.Random;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

public class DatabaseSharding {
	DB db;
	String dbName;	
	String hostname;
	int port;
	Mongo mongo;
	
	private static final int[] _shardPorts = { 27018, 27019 };

	public DatabaseSharding() {

	}
	public DatabaseSharding initManager(String hostname, int port, String dbName) throws UnknownHostException {
		this.dbName = dbName;
		this.hostname = hostname;
		this.port = port;
		this.dbName = dbName;
		this.mongo = new Mongo(new DBAddress(hostname, port, "admin"));
		this.db =  mongo.getDB(dbName);
		return null;

	}

	/**
	 * sets up the cluster with given db
	 * 
	 * @param none
	 */
	public void setupCluster() throws Exception {
	
		// Add the shards
		for (final int shardPort : _shardPorts) {
			final CommandResult result = mongo.getDB("admin").command(
					new BasicDBObject("addshard", ("localhost:" + shardPort)));
			System.out.println(result);
		}

		// Sleep for a bit to wait for all the nodes to be initialized.
		Thread.sleep(5000);

		// Enable sharding on a collection.
		CommandResult result = mongo.getDB("admin").command(new BasicDBObject("enablesharding", dbName));
		System.out.println(result);

		final BasicDBObject shardKey = new BasicDBObject("files_id", 1);
		String imageChunk = dbName + ".fs.chunks";
		final BasicDBObject cmd = new BasicDBObject("shardcollection", imageChunk);

		cmd.put("key", shardKey);

		//get the result from BasicDBObject
		result = mongo.getDB("admin").command(cmd);
		System.out.println(result);
	}

	/**
	 * saves the image into database
	 * 
	 * @param String
	 * 			Image name
	 */
	public String saveImage(String filename) throws Exception {
		final Random random = new Random(System.currentTimeMillis());
		String imageName = String.format("%02d", random.nextInt(30)) + filename;
		File imageFile = new File(filename);
		GridFS gfsPhoto = new GridFS(db, "photo");
		GridFSInputFile gfsFile = gfsPhoto.createFile(imageFile);
		gfsFile.setFilename(imageName);
		gfsFile.save();
		System.out.println("Image saved with name: " + imageName);		
		return imageName;
	}

	/**
	 * fetches the image into database
	 * 
	 * @param String
	 * 			Image name
	 * @return
	 * 		image file
	 */
	public File getImage(String filename) throws Exception {
		// find image from mongodb from image filename
		File file = new File(filename);
		GridFS gfsPhoto = new GridFS(db, "photo");
		GridFSDBFile imageOutput = gfsPhoto.findOne(filename);
		if (imageOutput != null) {
	        try {
	    		System.out.println("Image located from database: " + imageOutput);
	        	imageOutput.writeTo(file);
	          return file;
	        } catch (IOException e) {
	          e.printStackTrace();
	        }
		}
		return null;
	}

	/**
	 * removes the image from database
	 * 
	 * @param String
	 * 			Image name
	 * @return
	 * 		boolean value
	 */
	public boolean removeImage(String filename) throws Exception {
		// find image from mongodb and delete it
		GridFS gfsPhoto = new GridFS(db, "photo");
		if (gfsPhoto.findOne(filename) != null) {
			gfsPhoto.remove(gfsPhoto.findOne(filename));
			System.out.println("Image: " + filename + " has been deleted");
			return true;
		} else {
			System.out.println("Image could not be found");
			return false;
		}

	}
	
	public static void main(String args[]) throws Exception
	{
		DatabaseSharding dbShard = new DatabaseSharding();
		dbShard.initManager("127.0.0.1", 27017, "test");
		
		try {
			//set the cluster
			dbShard.setupCluster();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
