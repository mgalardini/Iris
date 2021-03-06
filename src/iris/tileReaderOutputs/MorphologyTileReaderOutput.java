/**
 * 
 */
package iris.tileReaderOutputs;

import ij.gui.Roi;

/**
 * @author George Kritikos
 *
 */
public class MorphologyTileReaderOutput extends BasicTileReaderOutput {

//			+ String.format("%.3f", readerOutputs[i][j].circularity) + "\t"
//			+ Integer.toString(readerOutputs[i][j].colonyOpacity) + "\t"
//			+ Integer.toString(readerOutputs[i][j].morphologyScoreFixedNumberOfCircles) + "\t"
//			+ Integer.toString(readerOutputs[i][j].morphologyScoreWholeColony) + "\t"
//			+ String.format("%.3f", readerOutputs[i][j].normalizedMorphologyScore) + "\t"
//			+ Integer.toString(readerOutputs[i][j].inAgarSize) + "\t"
//			+ String.format("%.3f", readerOutputs[i][j].inAgarCircularity) + "\t"
//			+ Integer.toString(readerOutputs[i][j].inAgarOpacity) + "\t"
//			+ Integer.toString(readerOutputs[i][j].wholeTileOpacity) + "\n");
			
	
	
		
	public int colonyOpacity = 0;
	
	/**
	 * This return value denotes the "wrinkliness" of the colony.
	 * The higher this number, the more complicated the colony structure
	 * This number comes from a pre-set limited number of circles
	 */
	public int morphologyScoreFixedNumberOfCircles = 0;
	
	/**
	 * This return value denotes the "wrinkliness" of the colony.
	 * The higher this number, the more complicated the colony structure
	 * This number comes from all the circles that fit in the colony and should be normalized by the colony size
	 */
	public int morphologyScoreWholeColony = 0;
	
	/**
	 * This return value is the same as the number above, but this time it's normalized
	 * against the size of the colony (since typically bigger colonies have better chances 
	 * of getting a higher morphology score)
	 * This is calculated as: normalizedMorphologyScore =  1000*morphologyScoreWholeColony/ColonySize
	 */
	public double normalizedMorphologyScore = 0;
	
	
	/**
	 * This is set to true if something went wrong getting the colony morphology levels
	 */
	public boolean errorGettingMorphology = false;
	
	
	/**
	 * If the colony has growth that extends into the agar, this should be set to true
	 * Note that in-agar growth can only be detected if it extends beyond the surface of the over-agar colony.
	 */
	public boolean colonyHasInAgarGrowth = false;
	
	
	/**
	 * The total size of the colony, including both over-agar and in-agar growth
	 */
	public int inAgarSize = 0;
	
	/**
	 * The total opacity of the colony, including both over-agar and in-agar growth
	 */
	public int inAgarOpacity = 0;
	
	/**
	 * The circularity of the colony, meaning it's in-agar ends
	 */
	public float inAgarCircularity = 0;
	
	/**
	 * The ROI of the total colony -- both in-agar and over-agar
	 */
	public Roi inAgarROI = null;

	/**
	 * The total opacity in the tile; may be a more accurate proxy of the in-agar growth, after subtracting 
	 * for the detected over-agar growth 
	 */
	public int wholeTileOpacity = 0;

	/**
	 * This holds the opacity of just the ring
	 */
	public int invasionRingOpacity = 0;

	
	/**
	 * This holds the size of just the ring
	 */
	public int invasionRingSize = 0;	
	
}
