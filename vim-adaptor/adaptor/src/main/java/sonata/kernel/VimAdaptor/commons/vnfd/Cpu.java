/**
 * Copyright (c) 2015 SONATA-NFV, UCL, NOKIA, NCSR Demokritos ALL RIGHTS RESERVED.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Neither the name of the SONATA-NFV, UCL, NOKIA, NCSR Demokritos nor the names of its contributors
 * may be used to endorse or promote products derived from this software without specific prior
 * written permission.
 * 
 * This work has been performed in the framework of the SONATA project, funded by the European
 * Commission under Grant number 671517 through the Horizon 2020 and 5G-PPP programmes. The authors
 * would like to acknowledge the contributions of their colleagues of the SONATA partner consortium
 * (www.sonata-nfv.eu).
 *
 * @author Dario Valocchi (Ph.D.), UCL
 * 
 */

package sonata.kernel.VimAdaptor.commons.vnfd;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Cpu {
  private int vcpus;
  @JsonProperty("cpu_support_accelerator")
  private String cpuSupportAccelerator;
  @JsonProperty("cpu_model")
  private String cpuModel;
  @JsonProperty("cpu_clock_speed")
  private String cpuClockSpeed;

  public int getVcpus() {
    return vcpus;
  }


  public void setVcpus(int vcpus) {
    this.vcpus = vcpus;
  }

  public void setCpuSupportAccelerator(String cpuSupportAccelerator) {
    this.cpuSupportAccelerator = cpuSupportAccelerator;
  }

  public void setCpuModel(String cpuModel) {
    this.cpuModel = cpuModel;
  }

  public void setCpuClockSpeed(String cpuClockSpeed) {
    this.cpuClockSpeed = cpuClockSpeed;
  }


  public String getCpuSupportAccelerator() {
    return cpuSupportAccelerator;
  }


  public String getCpuModel() {
    return cpuModel;
  }


  public String getCpuClockSpeed() {
    return cpuClockSpeed;
  }
}
