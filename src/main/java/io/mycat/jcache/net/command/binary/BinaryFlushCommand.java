package io.mycat.jcache.net.command.binary;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.enums.protocol.binary.ProtocolBinaryCommand;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.command.BinaryCommand;
import io.mycat.jcache.net.conn.Connection;
import io.mycat.jcache.net.conn.handler.BinaryResponseHeader;
import io.mycat.jcache.setting.Settings;
import io.mycat.jcache.util.ItemUtil;

/**
	Request:
	
	MAY have extras.
	MUST NOT have key.
	MUST NOT have value.
	
	Extra data for flush:
	
	     Byte/     0       |       1       |       2       |       3       |
	        /              |               |               |               |
	       |0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|
	       +---------------+---------------+---------------+---------------+
	      0| Expiration                                                    |
	       +---------------+---------------+---------------+---------------+
	     Total 4 bytes
	Response:
	
	MUST NOT have extras.
	MUST NOT have key.
	MUST NOT have value.
	Flush the items in the cache now or some time in the future as specified by the expiration field.
	 See the documentation of the textual protocol for the full description on how to specify the expiration time.
 * @author liyanjun
 *
 */
public class BinaryFlushCommand implements BinaryCommand{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryFlushCommand.class);
		
	@Override
	public void execute(Connection conn) throws IOException {
		
		if(logger.isDebugEnabled()){
			logger.debug("execute flush command");
		}

		long new_oldest = 0;
		
		if(!Settings.flushEnabled){
			writeResponse(conn,ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_FLUSH.getByte(), ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_AUTH_ERROR.getStatus(), 0L);
			return;
		}
		
		ByteBuffer extras = readExtras(conn);
		
		long exptime = extras.limit()>0?extras.getInt(4):0;
		
		exptime = exptime * 1000L + System.currentTimeMillis();
				
		if(exptime > 0){
			new_oldest = ItemUtil.realtime(exptime);
		}else{
			new_oldest = System.currentTimeMillis();
		}
		
		if(Settings.useCas){
			Settings.oldestLive = new_oldest - 1000;
			if(Settings.oldestLive < System.currentTimeMillis()){
				Settings.oldestCas = ItemUtil.get_cas_id();
			}
		}else{
			Settings.oldestLive = new_oldest;
		}
		//TODO STATS
//	    pthread_mutex_lock(&c->thread->stats.mutex);
//	    c->thread->stats.flush_cmds++;
//	    pthread_mutex_unlock(&c->thread->stats.mutex);
		BinaryResponseHeader header = buildHeader(conn.getBinaryRequestHeader(),ProtocolBinaryCommand.PROTOCOL_BINARY_CMD_FLUSH.getByte(),null,null,null,0l);
		writeResponse(conn,header,null,null,null);

	}
}
