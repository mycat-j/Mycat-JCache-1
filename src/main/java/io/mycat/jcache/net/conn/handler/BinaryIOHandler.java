package io.mycat.jcache.net.conn.handler;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.mycat.jcache.enums.conn.CONN_STATES;
import io.mycat.jcache.enums.protocol.binary.BinaryProtocol;
import io.mycat.jcache.enums.protocol.binary.ProtocolResponseStatus;
import io.mycat.jcache.net.JcacheGlobalConfig;
import io.mycat.jcache.net.command.BinaryCommand;
import io.mycat.jcache.net.command.Command;
import io.mycat.jcache.net.command.CommandContext;
import io.mycat.jcache.net.command.CommandType;
import io.mycat.jcache.net.conn.Connection;



public class BinaryIOHandler implements IOHandler{ 

	@Override
	public boolean doReadHandler(Connection conn) throws IOException {	
		
		BinaryCommand command = null;
		final ByteBuffer readbuffer = conn.getReadDataBuffer();
		int offset = conn.getLastMessagePos();
    	int limit  = readbuffer.position();
    	while(offset<limit){
            // 读取到了包头和长度
    		// 是否讀完一個報文
    		if(!validateHeader(offset, limit)) {
    			logger.debug("C#{}B#{} validate protocol packet header: too short, ready to handle next the read event offset{},limit{}",
    				conn.getId(), readbuffer.hashCode(),offset,limit);
    			return false; 
    		}
    		int length = getPacketLength(readbuffer,offset);
    		if((length + offset)> limit) {
    			logger.debug("C#{}B#{} nNot a whole packet: required length = {} bytes, cur total length = {} bytes, "
    			 	+ "ready to handle the next read event", conn.getId(), readbuffer.hashCode(), length, limit);
    			return false;
    		}
    		
    		/**
    		 * 解析 request header
    		 */
    		readRequestHeader(conn,readbuffer,offset);
    		
    		int keylen = conn.getBinaryRequestHeader().getKeylen();
    		int bodylen = conn.getBinaryRequestHeader().getBodylen();
    		int extlen  = conn.getBinaryRequestHeader().getExtlen();
    	    if (keylen > bodylen || keylen + extlen > bodylen) {
    	    	BinaryCommand.writeResponseError(conn, 
    	        						   conn.getBinaryRequestHeader().getOpcode(),
    	        						   ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_UNKNOWN_COMMAND.getStatus());
    	        conn.setWrite_and_go(CONN_STATES.conn_closing);
    	        return true;
    	    }
    	    
//          TODO    	    
//    	    if (settings.sasl && !authenticated(c)) {
//    	        write_bin_error(c, PROTOCOL_BINARY_RESPONSE_AUTH_ERROR, NULL, 0);
//    	        c->write_and_go = conn_closing;
//    	        return;
//    	    }
    	    conn.setNoreply(true);
    	    
    	    if(keylen > JcacheGlobalConfig.KEY_MAX_LENGTH) {
    	    	BinaryCommand.writeResponseError(conn, 
						   conn.getBinaryRequestHeader().getOpcode(),
						   ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_EINVAL.getStatus());
    			return true;
    	    }
    	    byte opcode = conn.getBinaryRequestHeader().getOpcode();
    		//执行命令
    		command = CommandContext.getCommand(opcode);
    		
    		if(command==null){
    			conn.setNoreply(false);
    			BinaryCommand.writeResponseError(conn, 
						   conn.getBinaryRequestHeader().getOpcode(),
						   ProtocolResponseStatus.PROTOCOL_BINARY_RESPONSE_UNKNOWN_COMMAND.getStatus());
    			conn.setWrite_and_go(CONN_STATES.conn_closing);
    			return true;
    		}else{
    			conn.setCurCommand(CommandType.getType(opcode));
        		command.execute(conn);
        		offset += length;
        		conn.setLastMessagePos(offset);
    		}
    	}
    	return true;
	}

	/**
	 * 解析请求头
	 * @param conn
	 * @return
	 * @throws IOException
	 */
	public void readRequestHeader(Connection conn,ByteBuffer buffer,int offset) throws IOException {
		BinaryRequestHeader header = conn.getBinaryRequestHeader();
		header.setMagic(buffer.get(0+offset));
		header.setOpcode(buffer.get(1+offset));
		header.setKeylen(buffer.getShort(2+offset));
		header.setExtlen(buffer.get(4+offset));
		header.setDatatype(buffer.get(5+offset));
		header.setReserved(buffer.getShort(6+offset));
		header.setBodylen(buffer.getInt(8+offset));
		header.setOpaque(buffer.getInt(12+offset));
		header.setCas(buffer.getLong(16+offset));
	}
	
	/**
	 * 校验报文头是否已经全部读取完成
	 * @param offset
	 * @param position
	 * @return
	 */
	private boolean validateHeader(final long offset, final long position){
		return position >= (offset + BinaryProtocol.memcache_packetHeaderSize);
	}
	
	/**
	 * 获取报文长度 
	 * @param buffer
	 * @return （报文头+报文内容）
	 * @throws IOException
	 */
	private int getPacketLength(ByteBuffer buffer,int offset) throws IOException{
		offset += 8;
		int length = buffer.get(offset) & 0xff  << 24;
		length |= (buffer.get(++offset) & 0xff) << 16;
		length |= (buffer.get(++offset) & 0xff) << 8;
		length |= (buffer.get(++offset) & 0xff) ;
		return length + BinaryProtocol.memcache_packetHeaderSize;
	}

}
