import io
import json
import os
import re

class TempData:
    region = ""
    mean = 0
    datapoints = []

def extract_temperature_data(raw_temp_data):
    temp_data = TempData()
    temp_data.mean = 0
    temp_data.datapoints = []

    # extract the relevant lines from the file
    for match in re.finditer("(\\d+), *(\\d+), *(\\d+), *(\\d+), *(\\d+)", raw_temp_data):
        staid = match.group(1) # station id
        souid = match.group(2) # source id
        date  = match.group(3) # date (YYYYMMDD)
        tg    = match.group(4) # mean temp in 0.1 °C
        q_tq  = match.group(5) # quality code for TG (0='valid'; 1='suspect'; 9='missing')

        if q_tq != "0":
            continue

        temp_data.mean += int(tg) / 10
        temp_data.datapoints.append({
            'date': date,
            'temp': int(tg) / 10
        })

    if len(temp_data.datapoints) != 0:
        temp_data.mean /= len(temp_data.datapoints)
    else:
        temp_data.mean = -1

    return temp_data

mean_temps = []
all_temps = []
data_directory = "./data"

for filename in os.listdir(data_directory):
    if not filename.endswith(".txt"):
        continue

    print("processing '" + filename + "'...")

    temp_data_file = open(data_directory + "/" + filename, 'r')
    raw_temp_data = temp_data_file.read()

    temp_data = extract_temperature_data(raw_temp_data)
    temp_data.region = filename.replace(".txt", "")
    
    mean_temp = temp_data.mean
    if mean_temp == -1:
        print("   no valid data points found")
        continue

    print("   mean temperature: " + str(mean_temp) + "°C")

    mean_temps.append({
        'region': temp_data.region,
        'meanTemp': mean_temp
    })

    all_temps.append(temp_data)

with open("../GeneratorAPI/resources/temperature/mean_temp.json", 'w') as result_file:
    json.dump(mean_temps, result_file, indent=4)

for data in all_temps:
    with open("../GeneratorAPI/resources/temperature/" + data.region + ".json", 'w') as result_file:
        json.dump(data.__dict__, result_file, indent=4)
