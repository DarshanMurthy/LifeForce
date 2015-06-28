import comm_pb2
import socket               
import time
import struct
import base64
import pdb

#added by harleen
def sendImage(file, filename):
    jobId = str(int(round(time.time() * 1000)))
    ownerId = 1
    r = comm_pb2.Request()    

    #preparing header
    #r.header.originator = "python client"  
    r.header.originator = 2  
    r.header.routing_id = comm_pb2.Header.JOBS
    #r.header.toNode = str(0)
    r.header.toNode = 1
    r.header.tag = str(1 + 2 + int(round(time.time() * 1000)))
    r.header.photoHeader.requestType = 1
    r.header.photoHeader.entryNode = 5570
    
    #preparing body
    r.body.ping.tag = str(1)
    r.body.ping.number = 2
    
    r.body.space_op.action = comm_pb2.NameSpaceOperation.ADDSPACE;   
    r.body.space_status.status = comm_pb2.SUCCESS
    
    #r.body.job_status.job_id = jobId;
    #r.body.job_status.status = comm_pb2.JobDesc.JOBRECEIVED;
    
    
    #adding file
    r.body.photoPayload.data = file
    r.body.photoPayload.name = filename    
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    r.body.job_op.job_id = jobId;
    
    r.body.job_op.data.name_space = "write"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "write"
     
    
    msg = r.SerializeToString()
    return msg

#added by harleen
def getImage(filename):
    jobId = str(int(round(time.time() * 1000)))
    ownerId = 1
    r = comm_pb2.Request()    

    #preparing header
    #r.header.originator = "python client"  
    r.header.originator = 2  
    r.header.routing_id = comm_pb2.Header.JOBS
    #r.header.toNode = str(0)
    r.header.toNode = 0
    r.header.tag = str(1 + 2 + int(round(time.time() * 1000)))
    r.header.photoHeader.requestType = 0
    
    #preparing body
    r.body.ping.tag = str(1)
    r.body.ping.number = 2
    
    r.body.space_op.action = comm_pb2.NameSpaceOperation.ADDSPACE;   
    r.body.space_status.status = comm_pb2.SUCCESS
    
    #r.body.job_status.job_id = jobId;
    #r.body.job_status.status = comm_pb2.JobDesc.JOBRECEIVED;
    
    
    #adding file
    r.body.photoPayload.name = filename    
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    r.body.job_op.job_id = jobId;
    
    r.body.job_op.data.name_space = "read"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "read"
     
    
    msg = r.SerializeToString()
    return msg

#added by harleen
def removeImage(filename):
    jobId = str(int(round(time.time() * 1000)))
    ownerId = 1
    r = comm_pb2.Request()    

    #preparing header
    #r.header.originator = "python client"  
    r.header.originator = 2  
    r.header.routing_id = comm_pb2.Header.JOBS
    #r.header.toNode = str(0)
    r.header.toNode = 0
    r.header.tag = str(1 + 2 + int(round(time.time() * 1000)))
    r.header.photoHeader.requestType = 2
    
    #preparing body
    r.body.ping.tag = str(1)
    r.body.ping.number = 2
    
    r.body.space_op.action = comm_pb2.NameSpaceOperation.ADDSPACE;   
    r.body.space_status.status = comm_pb2.SUCCESS
    
    #r.body.job_status.job_id = jobId;
    #r.body.job_status.status = comm_pb2.JobDesc.JOBRECEIVED;  
    
    #adding file
    r.body.photoPayload.name = filename    
    r.body.job_op.action = comm_pb2.JobOperation.ADDJOB
    r.body.job_op.job_id = jobId;
    
    r.body.job_op.data.name_space = "delete"
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "operation"
    r.body.job_op.data.options.value = "delete"
     
    
    msg = r.SerializeToString()
    return msg


def sendMsg(msg_out, port, host):
    s = socket.socket()         

    s.connect((host, port))        
    msg_len = struct.pack('>L', len(msg_out))    
    s.sendall(msg_len + msg_out)
    len_buf = receiveMsg(s, 4)
    msg_in_len = struct.unpack('>L', len_buf)[0]
    msg_in = receiveMsg(s, msg_in_len)
    
    r = comm_pb2.Request()
    r.ParseFromString(msg_in)
    s.close
    return r
def receiveMsg(socket, n):
    buf = ''    
    while n > 0:        
        #pdb.set_trace()        
        data = socket.recv(n) 
        print len(data) 
        print data                
        if data == '':
            raise RuntimeError('data not received!')
        buf += data
        print len(data)
        n -= len(data)
    return buf
 
       
if __name__ == '__main__':
    
     #call to convert file to byte string
   
    host = raw_input("IP:")
    port = raw_input("Port:")

    #host = "localhost"
    #port = 5572

    port = int(port)
    whoAmI = 1;

while True:    
    input = raw_input("Welcome to our MOOC client! Kindly select your desirable action:\n1.Upload Image\n2.Get Image\n3.Delete Image\n")
    if input == "1":
        filename = raw_input("Enter Full File Name with path:")
        #filename = "test.jpg"
        print filename        
    
        #open file entered by user
        with open(filename, "rb") as imageFile:
            imageConvert = base64.b64encode(imageFile.read())
        #convert file to byte
        imageByte = str.encode(imageConvert) 
      
        #call method to create request packet
        sendImageJob = sendImage(imageByte, filename) 
        result = sendMsg(sendImageJob, port, host)
        print result
        
        
        #format to get data from result string
        resultOperation = result.header.reply_code
        resultImageName = result.body.photoPayload.name
        if resultOperation == 2:
            print("Image has been sent successfully and saved at Server with name : " + resultImageName)
        else:
            print("A problem occurred. Please try again.")
 
    elif input == "2":        
        filename = raw_input("Enter file name: ")
        print filename        
    
        #call method to create request packet
        getImageJob = getImage(filename) 
        result = sendMsg(getImageJob, port, host)
        
        #format to get data from result string
        resultOperation = result.header.reply_code
        if resultOperation == 2:
            #create image from byte
            resultImage = result.body.photoPayload.data
            #pdb.set_trace()  
            fh = open(filename, "wb")
            fh.write(resultImage)
            fh.close()
            print("Image has been received successfully: " + filename)
        else:
            print("A problem occurred. Please try again.")
    
    elif input == "3":        
        filename = raw_input("Enter file name: ")
        print filename        
    
        #call method to create request packet
        removeImageJob = removeImage(filename) 
        result = sendMsg(removeImageJob, port, host)
        
        #format to get data from result string
        resultOperation = result.header.reply_code
        if resultOperation == 2:
            print("Image: " + filename + " has been deleted successfully")
        else:
            print("A problem occurred. Please try again.")            
    else:
            print("Bye. Thanks for using our System ...")
            break
               
                 


