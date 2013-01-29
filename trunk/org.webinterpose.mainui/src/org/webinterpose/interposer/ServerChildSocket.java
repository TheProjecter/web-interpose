package org.webinterpose.interposer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Date;

import org.webinterpose.mainui.InterposerView.Mapping;

public class ServerChildSocket implements Runnable {

	static int index = 0;
	ServerMainSocket server;
	Socket browserSoc;
	Socket webServerSock;

	File mappedFile = null;
	boolean mappedFileExists = false;

	private final String unicName = "t" + index++;

	public String getUnicName() {
		return browserSoc.getInetAddress().getHostAddress() + ":"
				+ browserSoc.getPort() + " on:" + browserSoc.getLocalPort();
	}

	public ServerChildSocket(Socket soc, ServerMainSocket server) {
		this.browserSoc = soc;
		this.server = server;
		int posColon = server.domain.indexOf(":");
		String host = server.domain;
		int port = 80;
		if (posColon != -1) {
			host = server.domain.substring(0, posColon);
			port = Integer.parseInt(server.domain.substring(posColon + 1));
		}
		try {
			trace("socket " + host + ":" + port);
			this.webServerSock = new Socket(host, port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		trace("New Thread ... " + unicName);
	}

	private void trace(String t) {
		System.out.println(unicName + ":" + t);
	}

	public void notify(String message) {
		server.notify(message, this);
	}

	public void doBreak() {
		if (browserSoc != null && !browserSoc.isClosed()) {
			try {
				browserSoc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private final static int BUFF_OFFSET = 1024 * 16;
	private final static int BUFF_SIZE = 1024 * 64;
	private final static int HEADER_BROWSER_SIZE = 1024;
	private final static int HEADER_SERVER_SIZE = 512;
	private final static int THE_WAIT_THAT_I_DONT_WANT = 100;

	private int transformRequestMessage(byte[] b, int length)
			throws IOException {
		// ugly because I do not want to call "write" 2 times,
		// so we do have to work with 1 buffer.
		String header = new String(b, BUFF_OFFSET, HEADER_BROWSER_SIZE);

		String resource = header.substring(4, header.indexOf(" HTTP/"));
		server.childListener.addFileUrl(resource);

		String headerChanged = header.replace("localhost:" + server.localPort,
				server.domain);
		mappedFile = null;
		mappedFileExists = false;
		if (server.mapOfMappedMapping.containsKey(resource)) {
			// avoid caches and compression if resource mapped to a file
			headerChanged = headerChanged
					.replace("Accept-Encoding", "AUO-invalid2")
					.replace("Cache-Control: ", "Cache-Control: no-cache,")
					.replace("If-Modified-Since", "AUO-invalid");
			Mapping m = server.mapOfMappedMapping.get(resource);

			mappedFile = new File(m.getLocalDirectory() + m.getLocalFilePath());
			mappedFileExists = mappedFile.exists(); // && mappedFile.canRead();
			if (!mappedFileExists) {
				File enclosingDir = mappedFile.getParentFile();
				enclosingDir.mkdirs();
			}
			trace("Mapped file exist: " + mappedFileExists + " url "
					+ m.getDistantFileUrl() + " File " + m.getLocalFilePath());
		}
		trace("AUO browser header " + headerChanged);
		byte[] b2 = headerChanged.getBytes();

		final int newOffset = BUFF_OFFSET - b2.length - HEADER_BROWSER_SIZE;
		if (newOffset < 0) {
			trace("INCREASE HEADER_BROWSER_SIZE (mainly because of big Cookies)!!");
		}

		for (int i = 0; i < b2.length; i++) {
			b[newOffset + i] = b2[i];
		}

		return newOffset;
	}

	private int computeFileBufferOffsetInServerMessage(byte[] b,
			boolean firstOcc) {
		int offset = 0;
		if (firstOcc) {
			String header = new String(b, 0, HEADER_SERVER_SIZE);
			// file comes after an empty line (CRLF)
			offset = header.indexOf("\r\n\r\n") + 4;

			trace("AUO offset " + offset + " server header " + header);
		}
		return offset;
	}

	private int buildServerReplyHeader(byte[] b) {
		String contentType = "text/html";
		String header = "HTTP/1.1 200 OK\r\n" + "Server: Apache-Coyote/1.1\r\n"
				+ "Content-Type: "+contentType+";charset=UTF-8\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "Vary: Accept-Encoding\r\n"
				+ "Date: "+ (new Date()).toString() +"\r\n"
				+ "\r\n";

		byte[] b2 = header.getBytes();
		if (b2.length > HEADER_SERVER_SIZE) {
			trace("INCREASE HEADER_SERVER_SIZE !!");
		}
		final int newOffset = HEADER_SERVER_SIZE - b2.length;
		for (int i = 0; i < b2.length; i++) {
			b[newOffset + i] = b2[i];
		}
		return newOffset;
	}

	@Override
	public void run() {
		byte[] b = new byte[BUFF_SIZE];
		InputStream isBrowser = null;
		OutputStream osBrowser = null;
		InputStream isWebServer = null;
		OutputStream osWebServer = null;
		FileOutputStream osMappedFile = null;
		FileInputStream isMappedFile = null;

		try {
			isBrowser = browserSoc.getInputStream();
			osBrowser = browserSoc.getOutputStream();
			isWebServer = webServerSock.getInputStream();
			osWebServer = webServerSock.getOutputStream();

			// TODO: ugly but works most of the time :(
			while (!server.toBreak
					&& (isWebServer.available() > 0
							|| isBrowser.available() > 0 || 
							(isMappedFile != null && isMappedFile.available() > 0))) {
				boolean mustTakeCareOfHeaders = true;
				int lengthRead = 0;
				while (!server.toBreak && isBrowser.available() > 0
						&& lengthRead != -1) {
					// read request and acknowledgment from browser and transmit
					// it to server until there is nothing to transmit from the browser
					lengthRead = isBrowser.read(b, BUFF_OFFSET, BUFF_SIZE
							- BUFF_OFFSET);
					trace("AUO0 loop "+lengthRead+new String(b,BUFF_OFFSET, 200));
					int newOffset = BUFF_OFFSET;

						newOffset = transformRequestMessage(b, lengthRead);
						mustTakeCareOfHeaders = false;

					osWebServer.write(b, newOffset, lengthRead + BUFF_OFFSET
							- newOffset);
					Thread.sleep(THE_WAIT_THAT_I_DONT_WANT);
				}
				
				if (isMappedFile != null) {
					isMappedFile.close();
					isMappedFile = null;
				}
				if (osMappedFile != null) {
					osMappedFile.close();
					osMappedFile = null;
				}

					if (mappedFile != null && mappedFileExists
							&& mappedFile.length() > 0) {
						isMappedFile = new FileInputStream(mappedFile);
					} else if (mappedFile != null
							&& (!mappedFileExists || mappedFile.length() == 0)) {
						osMappedFile = new FileOutputStream(mappedFile);
					}

				int lengthToWrite = 0;
				mustTakeCareOfHeaders = true;
				Arrays.fill(b, (byte) 0);

				if (isMappedFile != null) {
					// send existing file content to browser
					while (isMappedFile.available() > 0
							/* && isBrowser.available() */) {
						int bufferOffset = HEADER_SERVER_SIZE;
						int lengthFileRead = isMappedFile.read(b, bufferOffset,
								b.length - bufferOffset);
						if (lengthFileRead > 0) {
							if (mustTakeCareOfHeaders) {
								bufferOffset = buildServerReplyHeader(b);
								mustTakeCareOfHeaders = false;
							}
						}
						trace("AUO1 loop "+lengthFileRead);
						osBrowser.write(b, bufferOffset, lengthFileRead);
					}

					break;
				} else {
					// forward the content of the server message to the browser
					while (!server.toBreak
							&& lengthRead >= 0
							&& (mustTakeCareOfHeaders || isWebServer
									.available() > 0) && lengthToWrite != -1) {
						lengthToWrite = isWebServer.read(b);
						trace("AUO2 loop "+lengthToWrite + " " + new String(b, 0, 512));

						if (lengthToWrite > 0)
							osBrowser.write(b, 0, lengthToWrite);

						if (osMappedFile != null) {
							// save file if resource mapped (and file does not
							// exist
							int filePos = computeFileBufferOffsetInServerMessage(
									b, mustTakeCareOfHeaders);
							osMappedFile.write(b, filePos, lengthToWrite
									- filePos);
						}
						Thread.sleep(THE_WAIT_THAT_I_DONT_WANT);
						mustTakeCareOfHeaders = false;
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			trace("IOEXCEPTION !!!");
		} finally {
			try {
				if (isBrowser != null)
					isBrowser.close();
				if (osBrowser != null)
					osBrowser.close();
				if (isWebServer != null)
					isWebServer.close();
				if (osWebServer != null)
					osWebServer.close();
				if (osWebServer != null)
					osWebServer.close();
				if (osMappedFile != null)
					osMappedFile.close();
				if (isMappedFile != null)
					isMappedFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				trace("THREAD ENDDED");
			}
		}
	}
}
