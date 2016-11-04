import java.util.*;
import java.text.SimpleDateFormat;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;


class TestTrip {
	private static final String HOST = "0.0.0.0"; 
	private static final String IMEI = "205183767";
	private static final int ELEMENT_ID = 10527;
	private static final String DB_USER = "bpm2";
	private static final String DB_PASSWORD = "vintela";
	public static void main (String[] args) {
		
		//Carga driver Mysql:
		/*
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
*/
		Connection cn = null;

		try {
			String connectionString = String.format(
				"jdbc:mysql://localhost/pbpm?user=%s;password=%s", DB_USER, DB_PASSWORD);
			System.out.println("connectionString = '" + connectionString + "'");
			cn = DriverManager.getConnection(connectionString, DB_USER, DB_PASSWORD);
		} catch(SQLException sqle) {
			sqle.printStackTrace();
			System.exit(1);
		}

		try {
			System.out.println("Cargando plantilla position-template.txt");
			Path templatePath = FileSystems.getDefault().getPath(".", "position-template.txt");
			byte[] templateArray  = Files.readAllBytes(templatePath);
			String template = new String(templateArray);
			template = template.replaceAll("[\\r\\n]", "");
			System.out.println(String.format("Plantilla=%s", template));
			Path positionsPath = FileSystems.getDefault().getPath(".", "positions.txt");
			List<String> positions = Files.readAllLines(positionsPath);
			
			Socket socket = null;
			BufferedReader in = null;

			Date date = null;
			final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
			final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

			try {
				System.out.println("Inicializando Socket");
				socket = new Socket(HOST, 15103);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			} catch(UnknownHostException uhe) {
				System.err.println(String.format("No pude encontrar el host %s", HOST));
				System.exit(1);
			}
			boolean continuar = true;
			////while(continuar) {

				boolean firstPositionSent = false;
				for(String position : positions) {
					String[] positionParts = position.split(",");
					if (positionParts.length >= 2) {

						date = new Date();
						String message = template.replace("__IMEI__", IMEI)
							.replace("__DATE__", dateFormat.format(date))
							.replace("__TIME__", timeFormat.format(date))
							.replace("__LAT_WITH_SIGN__", "+" + truncateDecs(positionParts[0]))
							.replace("__LNG_WITH_SIGN__", truncateDecs(positionParts[1]))
							.replace("__BEARING__", positionParts[2])
						;
						//writer.println(message.replaceAll("\n","") + "\r");
						//writer.flush();
						String buffer = message + "\r";
						socket.getOutputStream().write(buffer.getBytes(), 0, buffer.length());
						socket.getOutputStream().flush();
						System.out.println(message);
						if (in.ready()) {
							char []buffercillo = new char[1024];
							in.read(buffercillo, 0, 1024);
							System.out.println("read");
						}
						if (!firstPositionSent) {
							String query = 	
								String.format("UPDATE element_status "
									+ " SET pos_lat=%s, pos_lng=%s "
									+ " WHERE element_id=%s",
									positionParts[0], positionParts[1],
									ELEMENT_ID)
							;
							Statement stmt = cn.createStatement();
							stmt.execute(query);
							query = 
								String.format("UPDATE gps_status "
									+ " SET tc_route_id=null, pos_lat=%s, pos_lng=%s "
									+ " WHERE element_id=%s",
									positionParts[0], positionParts[1],
									ELEMENT_ID);
							stmt.execute(query);
							query = 
								String.format("INSERT into gps_upd(gps_cfg_id, imei) "
									+ " values (24, %s) ",
									IMEI);
							stmt.execute(query);
							firstPositionSent = true;	
							Thread.sleep(2000);
							query = 
								String.format("UPDATE gps_status "
									+ " SET tc_route_id=20, pos_lat=%s, pos_lng=%s "
									+ " WHERE element_id=%s",
									positionParts[0], positionParts[1],
									ELEMENT_ID);
							stmt.execute(query);
							query = 
								String.format("INSERT into gps_upd(gps_cfg_id, imei) "
									+ " values (24, %s) ",
									IMEI);
							stmt.execute(query);
							Thread.sleep(2000);
						}
						Thread.sleep(1000);
						if (in.ready()) {
							char []buffercillo = new char[1024];
							in.read(buffercillo, 0, 1024);
							System.out.println("read");
						}
						
					}
				}
			////}
			//TODO: apagar el trip
			System.exit(0);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}

	private static String truncateDecs(String position) {
		return position;
		/*
		return String.format("%s.%s", 
			position.substring(0, position.indexOf('.')), 
			position.substring(position.indexOf('.') + 1, position.indexOf('.') + 7));
		*/
	}

}
