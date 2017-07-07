/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.location.cts.pseudorange;

import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.Array2DRowRealMatrix;

/**
 * Convert the Velocity in ECEF to Velocity vector in ENU coordination system.
 */

public class Ecef2EnuCovertor {

  /*
   Compute the Velocity in ENU system stored in {@code NeuVelocityValues} from position in ECEF:
   ecefXMeters, ecefYmeters, ecefZmeters and velocity in ECEF: ecefVelocityX, ecefVelocityY,
   ecefVelocityZ.
   */
  public static NeuVelocityValues convertECEFtoENU(double ecefXMeters, double ecefYmeters,
       double ecefZmeters, double ecefVelocityX, double ecefVelocityY, double ecefVelocityZ){

    RealMatrix rotationMatrix = new Array2DRowRealMatrix(3, 3);
    RealMatrix ecefVelocity = new Array2DRowRealMatrix(new double[][]{
        new double[]{ecefVelocityX},
        new double[]{ecefVelocityY},
        new double[]{ecefVelocityZ}});

    Ecef2LlaConvertor.GeodeticLlaValues latLngAlt = Ecef2LlaConvertor.convertECEFToLLACloseForm(
        ecefXMeters, ecefYmeters, ecefZmeters
    );

    // Fill in the rotation Matrix
    rotationMatrix.setEntry(0, 0, -1 * Math.cos(latLngAlt.longitudeRadians)
        * Math.sin(latLngAlt.latitudeRadians));
    rotationMatrix.setEntry(1, 0, -1 * Math.sin(latLngAlt.longitudeRadians));
    rotationMatrix.setEntry(2, 0, -1 * Math.cos(latLngAlt.longitudeRadians)
        * Math.cos(latLngAlt.latitudeRadians));
    rotationMatrix.setEntry(0, 1, -1 * Math.sin(latLngAlt.latitudeRadians)
        * Math.sin(latLngAlt.longitudeRadians));
    rotationMatrix.setEntry(1, 1, Math.cos(latLngAlt.longitudeRadians));
    rotationMatrix.setEntry(2, 1, -1 * Math.cos(latLngAlt.latitudeRadians)
        * Math.sin(latLngAlt.longitudeRadians));
    rotationMatrix.setEntry(0, 2, Math.cos(latLngAlt.latitudeRadians));
    rotationMatrix.setEntry(1, 2, 0);
    rotationMatrix.setEntry(2, 2, -1 * Math.sin(latLngAlt.latitudeRadians));

    RealMatrix enuResult = rotationMatrix.multiply(ecefVelocity);
    return new NeuVelocityValues(enuResult.getEntry(0, 0),
        enuResult.getEntry(1, 0), enuResult.getEntry(2 , 0));
  }

  /**
   * A class for containing the calculated values of Velocity in NEU coordinate system.
   */
  public static class NeuVelocityValues{
    public final double neuNorthVelocity;
    public final double neuEastVelocity;
    public final double neuUpVeloctity;

    public NeuVelocityValues(double neuNorthVelocity, double neuEastVelocity,
        double neuUpVeloctity){
      this.neuNorthVelocity = neuNorthVelocity;
      this.neuEastVelocity = neuEastVelocity;
      this.neuUpVeloctity = neuUpVeloctity;
    }
   }

}
