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
package poke.server.resources;

import poke.server.conf.ServerConf;
import eye.Comm.Request;

/**
 * an abstraction of the server's functionality (processes requests).
 * Implementations of this class will allow the server to handle a variety of
 * requests.
 * 
 * @author gash
 * 
 */
public interface Resource {

	/**
	 * called to process requests
	 * 
	 * @param request
	 * @return an action
	 */
	Request process(Request request);
	
	/**
	 * sets the configuration file
	 * 
	 * @param conf
	 */
	void setConfiguration(ServerConf conf);

}