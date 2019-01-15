import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.SheetsScopes;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class Main{
	public static void main(String[] args){
		processArgs(args);
	}

	private static void processArgs(String[] args){
		System.out.println(args.length);
		// One variable passed in is opening a file
		if(args.length == 1){
			Path pathToCSV = processPath(args[0]);
			new Head(pathToCSV);
		}else if(args.length == 3){
			// The user is giving credentials
			if(args[0].equals("--google") || args[0].equals("-g")){
				try{
					Credential credential = createCredentials(processPath(args[1]));
					String sheetID = args[2];
					new Head(credential, sheetID);
				}catch(IOException io){
					io.printStackTrace();
					exitWithError("Bad credential file");
				}catch(GeneralSecurityException security){
					exitWithError("Security error occurred, quiting program. ");
				}
			}
		}
	}

	private static Path processPath(String input){
		Path pathToFile = null; // argument converted to usage path by the program
		String errorMessage = "Bad file: " + input;
		try{
			if(input.startsWith("/")){
				// The user gave an absolute path from /
				pathToFile = new File(input).toPath();
			}else if(input.startsWith("~")){
				// The user gave an absolute path from ~
				pathToFile = new File(System.getProperty("user.home")).toPath().resolve(input);
			}else{
				pathToFile = new File(System.getProperty("user.dir")).toPath().resolve(input);
			}
		}catch(InvalidPathException e){
			// The file the user gave is incorrect
			exitWithError(errorMessage);
		}

		// Make sure the file exists and there is something to read
		if(!Files.exists(pathToFile)){
			exitWithError(errorMessage);
		}else{
			return pathToFile;
		}

		// This will never be called
		return null;
	}

	/**
	 * Process the path to the .json containing the credentials and pass it to the rest of the program.
	 * @return
	 */
	private static Credential createCredentials(Path path) throws IOException, GeneralSecurityException {
		// Read the json file for credential information
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		InputStream in = new FileInputStream(path.toFile());

		// Build credentials
		GoogleClientSecrets secrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
		GoogleAuthorizationCodeFlow.Builder flowBuilder = new GoogleAuthorizationCodeFlow.Builder(
				GoogleNetHttpTransport.newTrustedTransport(),
				jsonFactory,
				secrets,
				Collections.singletonList(SheetsScopes.SPREADSHEETS));
		// We are using the google database, which is online
		flowBuilder.setAccessType("online");
		GoogleAuthorizationCodeFlow flow = flowBuilder.build();

		// Open a port, -1 gives a free open port
		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(-1).build();

		// Make the credentials
		// We are not making a persisted credential store, so null for authorize user
		return new AuthorizationCodeInstalledApp(flow, receiver).authorize(null);
	}

	public static void exitWithError(String error){
		System.out.println(error);
		System.exit(1); // Edit with error
	}
}
