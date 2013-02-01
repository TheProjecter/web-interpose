package org.webinterpose.interposer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

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
	private final static int THE_WAIT_THAT_I_DONT_WANT = 250;
	private Mapping mapping = null;

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
			// [1] http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html (search 304)
			headerChanged = headerChanged
					.replace("Accept-Encoding", "AUO-invalid1")
					.replaceFirst("Cache-Control: .*", "AUO-invazlid2: toto")
					//"Cache-Control: no-cache") // see [1]
					.replace("If-Modified-Since", "AUO-invalid3")
					.replace("If-None-Match:", "AUO-invalid4");

			mapping = server.mapOfMappedMapping.get(resource);

			mappedFile = new File(mapping.getLocalDirectory()
					+ mapping.getLocalFilePath());
			mappedFileExists = mappedFile.exists(); // && mappedFile.canRead();
			if (!mappedFileExists) {
				File enclosingDir = mappedFile.getParentFile();
				enclosingDir.mkdirs();
			}
			trace("Mapped file exist: " + mappedFileExists + " url "
					+ mapping.getDistantFileUrl() + " path "
					+ mapping.getLocalFilePath());
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

	private String lookForHeaderField(final String headerField,
			final String header) {
		String payload = null;
		int l = headerField.length();
		int begin = header.indexOf(headerField) + l;
		int end = header.indexOf("\r\n", begin);
		if (begin != l - 1) {
			payload = header.substring(begin, end);
		} else {
			payload = null;
		}

		return payload;
	}

	private int computeFileBufferOffsetInServerMessage(byte[] b,
			boolean firstOcc) {
		int offset = 0;
		if (firstOcc) {
			String header = new String(b, 0, HEADER_SERVER_SIZE);

			if (mapping != null) {
				mapping.contentType = lookForHeaderField(
						"Content-Type: ", header);
				mapping.transferEncoding = lookForHeaderField(
						"Transfer-Encoding: ", header);
				mapping.contentEncoding = lookForHeaderField(
						"Content-Encoding: ", header);
			}
			// file comes after an empty line (CRLF)
			offset = header.indexOf("\r\n\r\n") + 4;

			trace("AUO00 mapping " + mapping.contentType + " " + mapping.transferEncoding + " " + mapping.contentEncoding);
			trace("AUO01 offset " + offset + " server header " + header);
		}
		return offset;
	}

	private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat(
			"EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
	
	private int buildServerReplyHeader(byte[] b) {
		String contentType = (mapping == null) ? "text/html;charset=UTF-8"
				: mapping.contentType;
		String header = "HTTP/1.1 200 OK\r\n" //+ "Server: Apache-Coyote/1.1\r\n"
				+ "Accept-Ranges: bytes\r\n"
				+ "Date: " + httpDateFormat.format(new Date()) + "\r\n"
				+ "Content-Type: " + contentType + "\r\n"
				//+ "Transfer-Encoding: chunked\r\n"
				//+ "Vary: Accept-Encoding\r\n"
				+ "Content-Length: " + (mappedFile.length()) + "\r\n"
				+ "\r\n";		

		byte[] b2 = header.getBytes();
		if (b2.length > HEADER_SERVER_SIZE) {
			trace("INCREASE HEADER_SERVER_SIZE !!");
		}
		final int newOffset = HEADER_SERVER_SIZE - b2.length;
		for (int i = 0; i < b2.length; i++) {
			b[newOffset + i] = b2[i];
		}

		trace("AUOAUOAUOAUOAUO "+ new String(b, newOffset, HEADER_SERVER_SIZE));
		return newOffset;
	}

	private int unchunckedBuffer(byte[] b, int filePos, int bytesRead) {
		int writePos = filePos;
		while (filePos < bytesRead) {
			int chunckSize = 0;
			// skip size bytes and compute chunckSize
			for (; filePos < bytesRead; filePos++) {
				byte currentByte = b[filePos];
				if (currentByte == 0x0D) {
					filePos+=2; // "\r\n before payload
					break;
				}

				// pipeline conditional
				currentByte = (byte) (currentByte - 0x30);
				currentByte = (byte) (currentByte > 0x9?currentByte - 0x7:currentByte);
				currentByte = (byte) (currentByte > 0xF?currentByte - 0x20:currentByte);
				chunckSize = chunckSize == 0?currentByte:(chunckSize << 4) + currentByte;
			}
			if (chunckSize == 0) break;
			System.arraycopy(b, filePos, b, writePos, chunckSize);
			writePos += chunckSize;
			filePos += chunckSize;
			filePos += 2; // "\r\n after payload
		}
		return writePos;
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
			while (!server.toBreak && (isWebServer.available() > 0
					|| isBrowser.available() > 0 
					|| (isMappedFile != null && isMappedFile.available() > 0))) {
				boolean mustTakeCareOfHeaders = true;
				int lengthRead = 0;
				//				while (!server.toBreak && isBrowser.available() > 0
				//						&& lengthRead != -1) {
				if (isBrowser.available() > 0) {
					// read request and acknowledgment from browser and transmit
					// it to server until there is nothing to transmit from the browser
					lengthRead = isBrowser.read(b, BUFF_OFFSET, BUFF_SIZE
							- BUFF_OFFSET);
					trace("AUO0 loop " + lengthRead
							+ new String(b, BUFF_OFFSET, 2048));
					int newOffset = BUFF_OFFSET;

					newOffset = transformRequestMessage(b, lengthRead);
					mustTakeCareOfHeaders = false;

					osWebServer.write(b, newOffset, lengthRead + BUFF_OFFSET
							- newOffset);
					Thread.sleep(THE_WAIT_THAT_I_DONT_WANT); // DO NOT REMOVE
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

				int bytesRead = 0;
				mustTakeCareOfHeaders = true;
				Arrays.fill(b, (byte) 0);

				if (isMappedFile != null) {
					// send existing file content to browser
					while (isMappedFile.available() > 0) {
						int bufferOffset = HEADER_SERVER_SIZE;
						int lengthFileRead = isMappedFile.read(b, bufferOffset,
								b.length - bufferOffset);
						if (lengthFileRead > 0) {
							if (mustTakeCareOfHeaders) {
								bufferOffset = buildServerReplyHeader(b);
								mustTakeCareOfHeaders = false;
							}
						}
						trace("AUO1 loop " + lengthFileRead);
						// AUO TODO fix that !!!
						osBrowser.write(b, bufferOffset, 
								lengthFileRead + HEADER_SERVER_SIZE - bufferOffset);
					}

					break;
				} else {
					// forward the content of the server message to the browser
					// TODO save all message to file and make see why it does not work on Chrome
					while (!server.toBreak
							&& lengthRead >= 0
							&& (mustTakeCareOfHeaders || isWebServer
									.available() > 0) && bytesRead != -1) {
						bytesRead = isWebServer.read(b);
						trace("AUO2 loop " + bytesRead + " " + new String(b, 0, 512));

						if (bytesRead > 0)
							osBrowser.write(b, 0, bytesRead);

						if (osMappedFile != null) {
							// save file if resource mapped (and file does not
							// exist
							int filePos = computeFileBufferOffsetInServerMessage(
									b, mustTakeCareOfHeaders);
							if (mapping != null && "chunked".equals(mapping.transferEncoding)) {
								int lastWritePos = unchunckedBuffer(b, filePos, bytesRead);
								trace("AUO-------"+(lastWritePos - filePos)+"----------- "+new String(b, filePos, 512));
								osMappedFile.write(b, filePos, lastWritePos - filePos);
							} else {
								//AUOrestore 
								osMappedFile.write(b, filePos, bytesRead - filePos);
								trace("aaaAAUOOOAUO "+filePos+" "+bytesRead);
								//osMappedFile.write(b, 0, bytesRead);
							}
						}
						Thread.sleep(THE_WAIT_THAT_I_DONT_WANT);
						mustTakeCareOfHeaders = false;
					}
				}
				Arrays.fill(b, (byte) 0);
			}
		} catch (IOException | InterruptedException e) {
			trace("IOEXCEPTION !!! "+ e.getMessage()+ " ");
			for ( StackTraceElement st : e.getStackTrace()) {
				trace("IOEXCEPTION !!!" + st.toString());
			}
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
