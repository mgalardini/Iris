/**
 * 
 */
package iris.profiles;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.AutoThresholder;
import ij.process.AutoThresholder.Method;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import iris.imageCroppers.GenericImageCropper;
import iris.imageSegmenterInput.BasicImageSegmenterInput;
import iris.imageSegmenterOutput.BasicImageSegmenterOutput;
import iris.imageSegmenters.RisingTideSegmenter;
import iris.imageSegmenters.SimpleImageSegmenter;
import iris.settings.BasicSettings;
import iris.tileReaderInputs.OpacityTileReaderInput;
import iris.tileReaderOutputs.OpacityTileReaderOutput;
import iris.tileReaders.OpacityTileReader;
import iris.ui.IrisFrontend;
import iris.utils.Toolbox;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * This profile is calibrated for use in measuring the colony sizes of E. coli or Salmonella 1536 plates
 * 
 * @author George Kritikos
 *
 */
public class EcoliOpacityProfile96 extends Profile {

	/**
	 * the user-friendly name of this profile (will appear in the drop-down list of the GUI) 
	 */
	private static String profileName = "E.coli Opacity Profile for 96 plates";


	/**
	 * this is a description of the profile that will be shown to the user on hovering the profile name 
	 */
	public static String profileNotes = "This profile is calibrated for use in measuring the colony sizes and opacities of E. coli in 96 plates";


	/**
	 * This holds access to the settings object
	 */
	private BasicSettings settings = new BasicSettings(IrisFrontend.settings);


	/**
	 * This function will analyze the picture using the basic profile
	 * The end result will be a file with the same name as the input filename,
	 * after the addition of a .iris ending
	 * @param filename
	 */
	public void analyzePicture(String filename){


		//0. initialize settings and open files for input and output
		//since this is a 384 plate, make sure the settings are redefined to match our setup
		if(IrisFrontend.singleColonyRun==false){
			settings.numberOfColumnsOfColonies = 12;
			settings.numberOfRowsOfColonies = 8;
		}
		//
		//--------------------------------------------------
		//
		//

		File file = new File(filename);
		String justFilename = file.getName();

		System.out.println("\n\n[" + profileName + "] analyzing picture:\n  "+justFilename);

		//initialize results file output
		StringBuffer output = new StringBuffer();
		output.append("#Iris output\n");
		output.append("#Profile: " + profileName + "\n");
		output.append("#Iris version: " + IrisFrontend.IrisVersion + ", revision id: " + IrisFrontend.IrisBuild + "\n");
		output.append("#"+filename+"\n");


		//1. open the image file, and check if it was opened correctly
		ImagePlus originalImage = IJ.openImage(filename);

		//check that file was opened successfully
		if(originalImage==null){
			//TODO: warn the user that the file was not opened successfully
			System.err.println("Could not open image file: " + filename);
			return;
		}
		
		//set flag to honour a possible user-set ROI
		if(IrisFrontend.singleColonyRun==true){
			if(filename.contains("colony_")){
				IrisFrontend.settings.userDefinedRoi=true; //doesn't hurt to re-set it
				originalImage.setRoi(new OvalRoi(0,0,originalImage.getWidth(),originalImage.getHeight()));
			}
			else if(filename.contains("tile_")){
				IrisFrontend.settings.userDefinedRoi=false; //doesn't hurt to re-set it
				originalImage.setRoi(new Roi(0,0,originalImage.getWidth(),originalImage.getHeight()));
			}
		}




		//
		//--------------------------------------------------
		//
		//

		//2. rotate the whole image
		double imageAngle = Toolbox.calculateImageRotation(originalImage);

		//create a copy of the original image and rotate it, then clear the original picture
		ImagePlus rotatedImage = Toolbox.rotateImage(originalImage, imageAngle);
		originalImage.flush();

		//output how much the image needed to be rotated
		if(imageAngle!=0){
			System.out.println("Image had to be rotated by  " + imageAngle + " degrees");
		}


		//
		//--------------------------------------------------
		//
		//

		//3. crop the plate to keep only the colonies
		ImagePlus croppedImage = GenericImageCropper.cropPlate(rotatedImage);

		//flush the original picture, we won't be needing it anymore
		rotatedImage.flush();




		//
		//--------------------------------------------------
		//
		//

		//4. pre-process the picture (i.e. make it grayscale)
		ImagePlus colourCroppedImage = croppedImage.duplicate();
		colourCroppedImage.setRoi(croppedImage.getRoi());
		
		ImageConverter imageConverter = new ImageConverter(croppedImage);
		imageConverter.convertToGray8();


		//
		//--------------------------------------------------
		//
		//

		//calculate the minimum and maximum grid spacings according to the cropped image size 
		//and the number of rows and columns, save the results in the settings object
		calculateGridSpacing(settings, croppedImage);

		//		//change the settings so that the distance between the colonies can now be smaller
		//		settings.minimumDistanceBetweenRows = 40;
		//		//..or larger
		//		settings.maximumDistanceBetweenRows = 100;






		//5. segment the cropped picture
		BasicImageSegmenterInput segmentationInput = new BasicImageSegmenterInput(croppedImage, settings);
		BasicImageSegmenterOutput segmentationOutput = SimpleImageSegmenter.segmentPicture(segmentationInput);

		//check if something went wrong
		if(segmentationOutput.errorOccurred){

			System.err.println("\n"+profileName+": unable to process picture " + justFilename);

			System.err.print("Image segmentation algorithm failed:\n");

			if(segmentationOutput.notEnoughColumnsFound){
				System.err.print("\tnot enough columns found\n");
			}
			if(segmentationOutput.notEnoughRowsFound){
				System.err.print("\tnot enough rows found\n");
			}
			if(segmentationOutput.incorrectColumnSpacing){
				System.err.print("\tincorrect column spacing\n");
			}
			if(segmentationOutput.notEnoughRowsFound){
				System.err.print("\tincorrect row spacing\n");
			}			


			//save the grid before exiting
			RisingTideSegmenter.paintSegmentedImage(croppedImage, segmentationOutput); //calculate grid image
			Toolbox.savePicture(croppedImage, filename + ".grid.jpg");

			return;
		}

		int x = segmentationOutput.getTopLeftRoi().getBounds().x;
		int y = segmentationOutput.getTopLeftRoi().getBounds().y;
		output.append("#top left of the grid found at (" +x+ " , " +y+ ")\n");

		x = segmentationOutput.getBottomRightRoi().getBounds().x;
		y = segmentationOutput.getBottomRightRoi().getBounds().y;
		output.append("#bottom right of the grid found at (" +x+ " , " +y+ ")\n");




		//
		//--------------------------------------------------
		//
		//

		//6. analyze each tile

		//create an array of measurement outputs
		OpacityTileReaderOutput [][] readerOutputs = new OpacityTileReaderOutput[settings.numberOfRowsOfColonies][settings.numberOfColumnsOfColonies];

		//for all rows
		for(int i=0;i<settings.numberOfRowsOfColonies;i++){
			//for all columns
			for (int j = 0; j < settings.numberOfColumnsOfColonies; j++) {
				readerOutputs[i][j] = OpacityTileReader.processTile(
						new OpacityTileReaderInput(croppedImage, segmentationOutput.ROImatrix[i][j], settings));

				//each generated tile image is cleaned up inside the tile reader
			}
		}



		//check if a row or a column has most of it's tiles empty (then there was a problem with gridding)
		//check rows first
		if(checkRowsColumnsIncorrectGridding(readerOutputs)){
			//something was wrong with the gridding.
			//just print an error message, save grid for debugging reasons and exit
			System.err.println("\n"+profileName+": unable to process picture " + justFilename);
			System.err.print("Image segmentation algorithm failed:\n");
			System.err.println("\ttoo many empty rows/columns");

			//calculate and save grid image
			Toolbox.drawColonyBounds(colourCroppedImage, segmentationOutput, readerOutputs);
			Toolbox.savePicture(colourCroppedImage, filename + ".grid.jpg");

			///HACK for Alex: removing the next return statement will make Iris print out the result even though the gridding failed  
			//return;
			///HACK end
		}

		//7. output the results

		//7.1 output the colony measurements as a text file
		output.append("row\tcolumn\tsize\tcircularity\topacity\n");
		//for all rows
		for(int i=0;i<settings.numberOfRowsOfColonies;i++){
			//for all columns
			for (int j = 0; j < settings.numberOfColumnsOfColonies; j++) {
				output.append(Integer.toString(i+1) + "\t" + Integer.toString(j+1) + "\t" 
						+ Integer.toString(readerOutputs[i][j].colonySize) + "\t"
						+ String.format("%.3f", readerOutputs[i][j].circularity) + "\t"
						+ Integer.toString(readerOutputs[i][j].opacity) + "\n");
			}
		}

		//check if writing to disk was successful
		String outputFilename = filename + ".iris";
		if(!writeOutputFile(outputFilename, output)){
			System.err.println("Could not write output file " + outputFilename);
		}
		else{
			//System.out.println("Done processing file " + filename + "\n\n");
			System.out.println("...done processing!");
		}



		//7.2 save any intermediate picture files, if requested
		settings.saveGridImage = true;
		if(settings.saveGridImage){
			//calculate grid image
			Toolbox.drawColonyBounds(colourCroppedImage, segmentationOutput, readerOutputs);
			Toolbox.savePicture(colourCroppedImage, filename + ".grid.jpg");
		}

	}


	/**
	 * This function calculates the minimum and maximum grid distances according to the
	 * cropped image size and
	 * the number of rows and columns that need to be found.
	 * Since the cropped image needs to be segmented roughly in equal distances, the
	 * nominal distance in which the coluns will be spaced apart will be
	 * nominal distance = image width / number of columns
	 * this should be equal to the (image height / number of rows), which is not calculated separately.
	 * Using this nominal distance, we can calculate the minimum and maximum distances, which are then used
	 * by the image segmentation algorithm. Distances that do in practice lead the segmentation algorithm
	 * to a legitimate segmentation of the picture are:
	 * minimum = 2/3 * nominal distance
	 * maximum = 4/3 * nominal distance
	 * 
	 * @param settings_
	 * @param croppedImage
	 */
	private void calculateGridSpacing(BasicSettings settings_,
			ImagePlus croppedImage) {

		int image_width = croppedImage.getWidth();
		float nominal_width = image_width / settings_.numberOfColumnsOfColonies;

		//save the results directly to the settings object
		settings_.minimumDistanceBetweenRows = Math.round(nominal_width*2/3);
		settings_.maximumDistanceBetweenRows = Math.round(nominal_width*4/3);

	}


	/**
	 * This function will check if there is any row or any column with more than half of it's tiles being empty.
	 * If so, it will return true. If everything is ok, it will return false.
	 * @param readerOutputs
	 * @return
	 */
	private boolean checkRowsColumnsIncorrectGridding(
			OpacityTileReaderOutput[][] readerOutputs) {

		int numberOfRows = readerOutputs.length;		
		if(numberOfRows==0)
			return(false);//something is definitely wrong, but probably not too many empty tiles

		int numberOfColumns = readerOutputs[0].length;



		//for all rows
		for(int i=0; i<numberOfRows; i++){
			int numberOfEmptyTiles = 0;
			//for all the columns this row spans
			for (int j=0; j<numberOfColumns; j++) {
				if(readerOutputs[i][j].colonySize==0)
					numberOfEmptyTiles++;
			}

			//check the number of empty tiles for this row 
			if(numberOfEmptyTiles>numberOfColumns/2)
				return(true); //we found one row that more than half of it's colonies are of zero size			
		}

		//do the same for all columns
		for (int j=0; j<numberOfColumns; j++) {
			int numberOfEmptyTiles = 0;
			//for all the rows this column spans
			for(int i=0; i<numberOfRows; i++){
				if(readerOutputs[i][j].colonySize==0)
					numberOfEmptyTiles++;
			}

			//check the number of empty tiles for this column 
			if(numberOfEmptyTiles>numberOfRows/2)
				return(true); //we found one row that more than half of it's colonies are of zero size			
		}

		return(false);
	}


	/**
	 * I cannot believe I have to write this
	 * @param list
	 * @return
	 */
	private static double getMean(ArrayList<Integer> list){

		int sum = 0;

		for(int i=0;i<list.size();i++){
			sum += list.get(i);
		}

		return(sum/list.size());
	}

	/**
	 * There has to be a better way guys..
	 * @param list
	 * @return
	 */
	static double getVariance(ArrayList<Integer> list){
		double mean = getMean(list);

		double sum = 0;

		for(int i=0;i<list.size();i++){			
			sum += Math.pow(list.get(i)-mean, 2);
		}

		return(sum/(list.size()-1));

	}


	/**
	 * This method will naively crop the plate in a hard-coded manner.
	 * It copies the given area of interest to the internal clipboard.
	 * Then, it copies the internal clipboard results to a new ImagePlus object.
	 * @param originalPicture
	 * @return
	 */
	public static ImagePlus cropImage(ImagePlus originalImage, Roi roi){
		originalImage.setRoi(roi);
		originalImage.copy(false);//copy to the internal clipboard
		//copy to a new picture
		ImagePlus croppedImage = ImagePlus.getClipboard();
		return(croppedImage);

	}

	/**
	 * Takes the grayscale cropped image and calculates the sum of the
	 * light intensity of it's columns (for every x)
	 * @param croppedImage
	 * @return
	 */
	private static ArrayList<Integer> sumOfRows(ImagePlus croppedImage){
		int dimensions[] = croppedImage.getDimensions();

		//make the sum of rows

		ArrayList<Integer> sumOfRows = new ArrayList<Integer>(dimensions[1]);

		int sum = 0;

		//for all rows
		for(int y=0; y<dimensions[1]; y++ ){
			sum = 0;

			//for all columns
			for(int x=0; x<dimensions[0]; x++ ){

				sum += croppedImage.getPixel(x, y)[0];
			}

			//sum complete, add it to the list
			//sumOfRows.set(y, sum);
			sumOfRows.add(sum);
		}

		return(sumOfRows);
	}





	/**
	 * Takes the grayscale cropped image and calculates the sum of the
	 * light intensity of it's rows (for every y)
	 * @param croppedImage
	 * @return
	 */
	private static ArrayList<Integer> sumOfColumns(ImagePlus croppedImage){
		int dimensions[] = croppedImage.getDimensions();

		//make the sum of rows and columns
		ArrayList<Integer> sumOfColumns = new ArrayList<Integer>(dimensions[0]);

		int sum = 0;

		//for all columns
		for(int x=0; x<dimensions[0]; x++ ){
			sum = 0;

			//for all rows
			for(int y=0; y<dimensions[1]; y++ ){

				sum += croppedImage.getPixel(x, y)[0];
			}

			//sum complete, add it to the list
			//sumOfColumns.set(x, sum);
			sumOfColumns.add(sum);
		}

		return(sumOfColumns);
	}


	/**
	 * This function will convert the given picture into black and white
	 * using the Otsu method. This version will also return the threshold found.
	 * @param 
	 */
	private static int turnImageBW_Otsu(ImagePlus grayscaleImage) {
		Calibration calibration = new Calibration(grayscaleImage);

		//2 things can go wrong here, the image processor and the 2nd argument (mOptions)
		ImageProcessor imageProcessor = grayscaleImage.getProcessor();

		ImageStatistics statistics = ImageStatistics.getStatistics(imageProcessor, ij.measure.Measurements.MEAN, calibration);
		int[] histogram = statistics.histogram;

		AutoThresholder at = new AutoThresholder();
		int threshold = at.getThreshold(Method.Otsu, histogram);

		imageProcessor.threshold(threshold);

		//BW_croppedImage.updateAndDraw();

		return(threshold);
	}

	/**
	 * This function writes the contents of the string buffer to the file with the given filename.
	 * This function was written solely to hide the ugliness of the Exception catching from the Profile code.
	 * @param outputFilename
	 * @param output
	 * @return
	 */
	private boolean writeOutputFile(String outputFilename, StringBuffer output) {

		FileWriter writer;

		try {
			writer = new FileWriter(outputFilename);
			writer.write(output.toString());
			writer.close();

		} catch (IOException e) {
			return(false); //operation failed
		}

		return(true); //operation succeeded
	}



}
