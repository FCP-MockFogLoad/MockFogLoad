import io
import json
import os
import re

def extract_mean_temperature(raw_temp_data):
    mean = 0
    count = 0

    for match in re.finditer("(\\d+), *(\\d+), *(\\d+), *(\\d+), *(\\d+)", raw_temp_data):
        staid = match.group(1) # station id
        souid = match.group(2) # source id
        date  = match.group(3) # date (YYYYMMDD)
        tg    = match.group(4) # mean temp in 0.1 °C
        q_tq  = match.group(5) # quality code for TG (0='valid'; 1='suspect'; 9='missing')

        if q_tq != "0":
            continue

        count += 1
        mean += int(tg) / 10

    if count == 0:
        return -1

    return mean / count

mean_temps = {}
data_directory = "./data"

for filename in os.listdir(data_directory):
    if not filename.endswith(".txt"):
        continue

    print("processing '" + filename + "'...")

    temp_data_file = open(data_directory + "/" + filename, 'r')
    raw_temp_data = temp_data_file.read()
    mean_temp = extract_mean_temperature(raw_temp_data)

    if mean_temp == -1:
        print("   no valid data points found")
        continue

    print("   mean temperature: " + mean_temp + "°C")

    city = filename.replace(".txt", "")
    mean_temps[city] = mean_temp

with open("./mean_temp.json", 'w') as result_file:
    json.dump(mean_temps, result_file)
