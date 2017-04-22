package io.mycat.jcache.net.command.binary;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.jcache.net.command.BinaryCommand;
import io.mycat.jcache.net.conn.Connection;


/**
	These commands will either add or remove the specified amount to the requested counter. 
	If you want to set the value of the counter with add/set/replace, 
	the objects data must be the ascii representation of the value and not the byte values of a 64 bit integer.
	
	If the counter does not exist, one of two things may happen:
	
	If the expiration value is all one-bits (0xffffffff), the operation will fail with NOT_FOUND.
	For all other expiration values, the operation will succeed by seeding the value for this key 
	with the provided initial value to expire with the provided expiration time. The flags will be set to zero.
	Decrementing a counter will never result in a "negative value" (or cause the counter to "wrap"). 
	instead the counter is set to 0. Incrementing the counter may cause the counter to wrap.
       
 * @author liyanjun
 *
 */
public class BinaryDecrQCommand implements BinaryCommand{
	
	private static final Logger logger = LoggerFactory.getLogger(BinaryDecrQCommand.class);
		
	@Override
	public void execute(Connection conn) throws IOException {
		complete_incr_bin(conn);
	}
}
